package ru.spbau.anastasia.race;

import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class BluetoothService extends Service {

    public boolean isServer;
    public boolean isBegin;
    public static final String TAG = "BluetoothService";
    private static final UUID MY_UUID = UUID.fromString("27e86a38-a29c-421e-9d17-fe9c0c3bf2e6");

    public static final int NOTIFICATION_ID = 1;

    private BluetoothAdapter btAdapter;
    private BluetoothSocket btSocket;

    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (btSocket != null) {
            try {
                btSocket.close();
            } catch (IOException ignored) { }
            btSocket = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    public class BtBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
    }
    private final IBinder binder = new BtBinder();


    public static class BtUnavailableException extends Exception {
        public BtUnavailableException() {
            super("bluetooth is not supported");
        }
    }

    public void initBtAdapter() throws BtUnavailableException {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            throw new BtUnavailableException();
        }
        isBegin = false;
    }

    public boolean isConnected() {
        return connectedThread != null;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return btAdapter;
    }

    public interface OnConnected {
        FutureTask success();
    }
    private OnConnected onConnected;
    public void setOnConnected(OnConnected onConnected) {
        this.onConnected = onConnected;
    }

    public synchronized void startAcceptThread() {
        acceptThread = new AcceptThread();
        acceptThread.start();
    }

    public synchronized void startConnectThread(String address) {
        connectThread = new ConnectThread(address);
        connectThread.start();
    }

    public interface OnMessageReceived {
        void process(int bytes, byte[] buffer);
    }
    private OnMessageReceived onMessageReceived;
    ArrayList<Integer> sizes = new ArrayList<>();
    ArrayList<byte[]> cache = new ArrayList<>();
    public void setOnMessageReceived(OnMessageReceived onMessageReceived) {
        this.onMessageReceived = onMessageReceived;

        for (int i = 0; i < cache.size(); ++i) {
            onMessageReceived.process(sizes.get(i), cache.get(i));
        }
        cache.clear();
        sizes.clear();
    }

    public void write(byte[] bytes) {
        connectedThread.write(bytes);
    }

    public BluetoothSocket getBluetoothSocket() {
        return btSocket;
    }

    public void showNotification(Class<?> aClass, String string) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setContentTitle(string)
                .setContentText(string)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, aClass), 0));

        startForeground(NOTIFICATION_ID, builder.build());
    }

    private synchronized void connected(BluetoothSocket socket) throws ExecutionException, InterruptedException {
        this.btSocket = socket;

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        FutureTask futureTask = onConnected.success();
        try {
            Integer integer = (Integer) futureTask.get();
        } catch (InterruptedException e){

        }

        isBegin = true;
        connectedThread = new ConnectedThread();
        connectedThread.start();
    }

    private class AcceptThread extends Thread {
        private BluetoothServerSocket serverSocket;

        public AcceptThread() {
            try {
                serverSocket = btAdapter.listenUsingRfcommWithServiceRecord(TAG, MY_UUID);
            } catch (IOException ignored) { }
        }

        public void run() {
            BluetoothSocket socket;
            while (true) {
                try {
                    socket = serverSocket.accept();
                    isServer = true;
                } catch (IOException e) {
                    break;
                }
                if (socket != null) {
                    try {
                        connected(socket);
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    try {
                        serverSocket.close();
                    } catch (IOException ignored) { }
                    break;
                }
            }
        }

        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException ignored) { }
        }
    }

    private class ConnectThread extends Thread {
        private BluetoothSocket socket;
        public static final String TAG = "ConnectThread";

        public ConnectThread(String address) {
            try {
                socket = btAdapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException ignored) { }
        }

        public void run() {
            btAdapter.cancelDiscovery();

            try {
                socket.connect();
                isServer = false;
                Log.d(TAG, Boolean.toString(socket.isConnected()));
            } catch (IOException connectException) {
                try {
                    socket.close();
                } catch (IOException ignored) { }
                return;
            }

            BluetoothSocket tmp = socket;
            socket = null;
            try {
                connected(tmp);
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public void cancel() {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) { }
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ConnectedThread() {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = btSocket.getInputStream();
                tmpOut = btSocket.getOutputStream();
            } catch (IOException ignored) {
                Log.d(TAG, "error");
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    Log.d(TAG, "read");
                    try {
                        onMessageReceived.process(bytes, buffer);
                    } catch (NullPointerException ignored) {
                        sizes.add(bytes);
                        cache.add(buffer);
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
                Log.d(TAG, "write");
            } catch (IOException ignored) { }
        }

        public void cancel() {
            try {
                btSocket.close();
            } catch (IOException ignored) { }
        }
    }
}
