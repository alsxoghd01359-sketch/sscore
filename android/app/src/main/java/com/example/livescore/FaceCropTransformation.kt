package com.example.livescore

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest
import kotlin.math.max

/**
 * 선수 프로필 사진에서 얼굴 영역을 확대해서 보여주는 Glide 변환.
 * 이미지를 2.2배 확대한 뒤 위쪽에서 25% 내려온 위치를 중심으로 크롭.
 * 이후 CircleCrop 과 함께 사용: transform(FaceCropTransformation(), CircleCrop())
 */
class FaceCropTransformation : BitmapTransformation() {

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val scale = max(
            outWidth.toFloat()  / toTransform.width,
            outHeight.toFloat() / toTransform.height
        ) * 1.25f

        val scaledW = (toTransform.width  * scale).toInt().coerceAtLeast(outWidth)
        val scaledH = (toTransform.height * scale).toInt().coerceAtLeast(outHeight)

        val scaled = Bitmap.createScaledBitmap(toTransform, scaledW, scaledH, true)

        // 가로: 정중앙 크롭  /  세로: 위쪽 25% 지점부터 크롭 (얼굴 집중)
        val left = ((scaledW - outWidth)  / 2).coerceAtLeast(0)
        val top  = ((scaledH - outHeight) / 4).coerceAtLeast(0)

        return Bitmap.createBitmap(
            scaled,
            left, top,
            minOf(outWidth,  scaledW - left),
            minOf(outHeight, scaledH - top)
        )
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("FaceCropTransformation_v2".toByteArray(Charsets.UTF_8))
    }

    override fun equals(other: Any?) = other is FaceCropTransformation
    override fun hashCode() = javaClass.hashCode()
}