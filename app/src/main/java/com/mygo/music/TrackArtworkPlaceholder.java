package com.mygo.music;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** A local music tile while the source cover is absent or loading. Never invents album artwork. */
final class TrackArtworkPlaceholder extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path note=new Path();
    private final RectF tile=new RectF();private Shader gradient;private final int color;
    private int alpha=255;private ColorFilter filter;
    TrackArtworkPlaceholder(String identity){int[] colors={0xff665784,0xff456b7b,0xff866454,0xff52668b,0xff6f5978};color=colors[(identity.hashCode()&0x7fffffff)%colors.length];note.moveTo(11,7);note.lineTo(11,17);note.moveTo(11,7);note.lineTo(18,5);note.lineTo(18,15);}
    @Override protected void onBoundsChange(Rect bounds){tile.set(bounds.left,bounds.top,bounds.right,bounds.bottom);gradient=new LinearGradient(bounds.left,bounds.top,bounds.right,bounds.bottom,color,0xff252434,Shader.TileMode.CLAMP);}
    @Override public void draw(Canvas canvas){Rect b=getBounds();paint.setColorFilter(filter);paint.setAlpha(alpha);paint.setShader(gradient);paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(tile,Math.min(b.width(),b.height())*.17f,Math.min(b.width(),b.height())*.17f,paint);paint.setShader(null);int save=canvas.save();float side=Math.min(b.width(),b.height());canvas.translate(b.exactCenterX()-side/2,b.exactCenterY()-side/2);canvas.scale(side/24f,side/24f);paint.setColor(0xffe2e8f4);paint.setAlpha(alpha);paint.setStrokeWidth(1.4f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);canvas.drawPath(note,paint);paint.setStyle(Paint.Style.FILL);canvas.drawOval(6,15,11,19,paint);canvas.drawOval(13,13,18,17,paint);canvas.restoreToCount(save);}
    @Override public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){this.filter=filter;invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
