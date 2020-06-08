package com.findyourvenue.findyourvenue.activity

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.exifinterface.media.ExifInterface
import com.findyourvenue.findyourvenue.MainActivity.Companion.cursor
import com.findyourvenue.findyourvenue.MainActivity.Companion.listId
import com.findyourvenue.findyourvenue.R
import com.findyourvenue.findyourvenue.data.Venue
import com.findyourvenue.findyourvenue.provider.MyContentProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Classe StoreImage: classe che implementa una semplice ListView con scelta dove l'utente puo'
 * selezionare a quale delle località memorizzate nel database associare una nuova foto presa dalla galleria
 * o scattata tramite fotocamera
 */
class StoreImage : AppCompatActivity() {

    //Intero per recuperare l'ID della localita da aggiornare
    private var position : Int = -1

    //Array di byte dove memorizzare i byte della foto ricevuti via Intent
    private var imageBytes : ByteArray? = null

    /**
     * Path alla foto: nel caso 1, è una stringa è la foto è stata salvata nella cache locale dell'applicazione,
     * in caso 2 è una URI alla foto presente in galleria
     */
    private var pathToPic : String? = null

    //Per distinguere tra foto scattata e presa dalla galleria
    private var case : Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store_image)

        supportActionBar?.title = resources.getString(R.string.choice_venue)

        case = intent.extras?.getInt("case",0)!!

        /**
         * Lancio una coroutine per caricare in background i bytes della foto
         */
        pathToPic = intent.getStringExtra("path")
        val job = if (pathToPic != null) {
            GlobalScope.launch {
                withContext(Dispatchers.IO) {
                    getBytesFromPhoto()
                }
            }
        }
        else {
            null
        }

        val listView : ListView = findViewById(R.id.listview_store)

        /**
         * Utilizzo un CursorAdapter per mostrare i nomi delle località salvate nella listview
         */
        if (cursor != null) {
            listView.adapter = MyCursorAdapter(
                this@StoreImage,
                cursor
            )
        }


        listView.choiceMode = ListView.CHOICE_MODE_SINGLE

        listView.setOnItemClickListener{ _: AdapterView<*>, _: View, i: Int, _: Long ->
            Log.d("call","Selected item $i")
            position = i
        }

        //Chiama la AsyncUpdate per aggiornare la relativa entry nel database
        val fab : FloatingActionButton = findViewById(R.id.fab)
        fab.setOnClickListener{
            if (position == -1) {
                finish()
            }
            else {
                Log.d("call", "Elemento selezionato: $position")
                //SALVA LA FOTO
                /**
                 * Coroutine utilizzata per aggiornare la località con la nuova foto, come nel caso di prima,
                 * viene chiamato il contentResolver qui, mai sul thread della UI.
                 * Viene chiamato il metodo update del MyContentProvider che effettua una @Query per aggiornare solo il valore della fotografia.
                 * Questa coroutine controlla che prima la coroutine precedente abbia finito, per avere la sicurezza che i byte siano presenti!
                 */
                GlobalScope.launch {
                    withContext(Dispatchers.IO) {
                        if (job?.isCompleted!!) {
                            val longId = listId[position].toLong()

                            val contentValues = ContentValues()
                            contentValues.put(Venue.COLUMN_VENUE_ID, longId)
                            Log.d("call", "id : $longId")
                            val byteImages = ByteArray(imageBytes?.size!!)
                            Log.d("call", "Byte della foto? : ${imageBytes?.size!!}")
                            imageBytes?.copyInto(byteImages, 0, 0, imageBytes?.size!!)
                            contentValues.put(Venue.COLUMN_SAVED_PICS, byteImages)
                            contentResolver.update(
                                MyContentProvider.URI_VENUE.buildUpon()
                                    .appendPath(longId.toString()).build(),
                                contentValues,
                                null,
                                null
                            )
                        }

                    }
                }

                Toast.makeText(this, R.string.photo_saved, Toast.LENGTH_SHORT).show()
                val intent = Intent()
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }
    }

    /**
     * Funzione suspend chiamata dalla Coroutine, la utilizzo per ottenere i byte dal path di foto passato via Intent.
     * Se siamo nel caso 1 (fotocamera) allora utilizzo direttamente il path restituito dalla fotografia,
     * altrimenti utilizzo la Uri restituita dalla foto presa dalla galleria
     */
    private suspend fun getBytesFromPhoto() = withContext(Dispatchers.IO) {

        val imageStream = if (case == 1) {
            contentResolver.openInputStream(Uri.fromFile(File(pathToPic!!)))
        } else {
            contentResolver.openInputStream(Uri.parse(pathToPic!!))
        }
        var selectedImage: Bitmap = BitmapFactory.decodeStream(imageStream)
        selectedImage = getResizedBitmap(selectedImage)

        val ei =
        if (case == 1) {
            ExifInterface(pathToPic!!)
        }
        else {
            ExifInterface(imageStream!!)
        }

        val orientation: Int = ei.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        val rotatedBitmap: Bitmap?
        rotatedBitmap = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(selectedImage, 90F)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(selectedImage, 180F)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(selectedImage, 270F)
            ExifInterface.ORIENTATION_NORMAL -> selectedImage
            else -> selectedImage
        }

        val stream = ByteArrayOutputStream()
        rotatedBitmap?.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        imageBytes = stream.toByteArray()
        selectedImage.recycle()
        rotatedBitmap?.recycle()
        stream.close()
        Log.d("call","Fine di questa coroutine, byte della foto ottenuti")
    }

    /**
     * rotateImage: funzione di supporto per ruotare l'immagine, solitamente le foto prese da fotocamera vengono automaticamente salvate
     * in modalità landscape, in questo modo tornano all'originale
     */
    private fun rotateImage(source: Bitmap, angle: Float): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }

    /**
     * metodo getResizedBitmap: utilizzato per diminuire la qualità delle fotografie prese dalla galleria o da camera,
     * per evitare di occupare troppa memoria in database!
     */
    private fun getResizedBitmap(image: Bitmap): Bitmap {
        var width = image.width
        var height = image.height
        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = 500
            height = (width / bitmapRatio).toInt()
        } else {
            height = 500
            width = (height * bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(image, width, height, true)
    }

}