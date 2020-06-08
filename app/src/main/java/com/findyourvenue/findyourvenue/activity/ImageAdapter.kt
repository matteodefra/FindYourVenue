package com.findyourvenue.findyourvenue.activity

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.viewpager.widget.PagerAdapter
import com.findyourvenue.findyourvenue.R
import com.squareup.picasso.Picasso

/**
 * Classe ImageAdapter: eredita da PagerAdapter, viene utilizzata per mostrare il carosello di foto all'interno di un ViewPager
 *
 * @param stringArray array di stringhe contenente le URLs da cui scaricare le foto. Per le foto viene utilizzata la libreria esterna
 *                      Picasso, che implementa al suo interno un AsyncTask e funzionalità di caching
 * @param byteArray array di byte da cui decodificare l'eventuale foto aggiunta successivamente (nel caso in cui la località è gia stata
 *                  memorizzata nel database
 */
class ImageAdapter(private var stringArray: ArrayList<String>,private var byteArray: ByteArray?) : PagerAdapter() {

    /**
     * Uso la libreria Picasso per scaricare le foto e la BitmapFactory per convertire il byteArray in Bitmap
     */
    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val imageView = ImageView(container.context)

        imageView.scaleType = ImageView.ScaleType.FIT_CENTER

        try {
            val color: Int = Color.rgb(192, 192, 192)

            val gradientDrawable = GradientDrawable()
            gradientDrawable.shape = GradientDrawable.RECTANGLE
            gradientDrawable.setColor(color)
            Picasso.get().load(stringArray[position])
                .placeholder(gradientDrawable)
                .error(R.drawable.ic_broken_image_black_24dp)
                .into(imageView)
        } catch (e : IndexOutOfBoundsException) {
            val options = BitmapFactory.Options()
            options.inMutable = true
            val bmp =
                BitmapFactory.decodeByteArray(byteArray, 0, byteArray!!.size, options)
            imageView.setImageBitmap(bmp)
        }

        container.addView(imageView)

        return imageView
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view == `object` as ImageView
    }

    /**
     * getCount
     *
     * @return la lunghezza dell'array di stringhe + 1 se è presente anche il byteArray, altrimenti stringArray.size
     */
    override fun getCount(): Int {
        if (byteArray != null) {
            if (byteArray?.size!! > 1) {
                return stringArray.size + 1
            }
        }
        return stringArray.size
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as ImageView)
    }


}