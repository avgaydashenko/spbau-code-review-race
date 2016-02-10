package ru.spbau.anastasia.race;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;

public class mLive extends mSimpleSprite {

    private static int typeOfGame;
    private int numOfAlive;

    public static final int FIRST_PLAYER = 1;
    public static final int SECOND_PLAYER = 2;

    public mLive(Resources res, int type_, float height_) {
        super(0, 10, 0, 0, init(res), height_);
        typeOfGame = type_;
        numOfAlive = 3;
        recalcX();
        type = TYPE_LIVE;
    }

    private void recalcX() {
        if (typeOfGame == FIRST_PLAYER) {
            x = 10;
        } else {
            x = mSettings.CurrentXRes - 200;
        }
    }

    private static Bitmap init(Resources res) {
        Bitmap bmp = BitmapFactory.decodeResource(res, R.drawable.live);
        return Bitmap.createScaledBitmap(bmp, 40, 40, false);
    }

    @Override
    void update() {
    }

    void update(mPlayerSprite player) {
        numOfAlive = player.getLive();
    }

    @Override
    public void draw(Canvas c, Paint p) {
        for (int i = 0; i < numOfAlive; i++) {
            c.drawBitmap(bmp, x + i * 50, y, p);
        }
    }
}
