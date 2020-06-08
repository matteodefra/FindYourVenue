package com.findyourvenue.findyourvenue.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import androidx.viewpager.widget.ViewPager
import com.findyourvenue.findyourvenue.MainActivity.Companion.geocoder
import com.findyourvenue.findyourvenue.R
import com.findyourvenue.findyourvenue.data.AllVenue
import com.findyourvenue.findyourvenue.data.Venue
import com.findyourvenue.findyourvenue.provider.MyContentProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList

class ActivityVenue : AppCompatActivity() {

    //ViewPager per il carosello di foto
    private lateinit var viewPager: ViewPager

    //ImageButton per l'aggiunta o rimozione dal database
    private lateinit var buttonChoice : ImageButton

    //TextView per mostrare le informazioni sul luogo
    private lateinit var addressText : TextView
    private lateinit var hoursText: TextView
    private lateinit var description : TextView

    //TextToSpeech per il sintetizzatore vocale della descrizione
    private var tts : TextToSpeech? = null

    //Un oggetto di tipo Venue per il recupero dell'oggetto
    private var venue : Venue = Venue()

    //Stringa utilizzata dal Geocoder per memorizzare l'indirizzo trovato
    private var address : String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_venue)

        val imageView : ImageView = findViewById(R.id.buttonchoice)

        val playDescription : ImageButton = findViewById(R.id.play_description)

        buttonChoice = findViewById(R.id.buttonchoice)
        addressText = findViewById(R.id.place_address)
        hoursText = findViewById(R.id.hours_value)
        description = findViewById(R.id.venue_description)

        val position : Int = intent.getIntExtra("position",0)
        val type : Int = intent.getIntExtra("type",2)

        /**
         * Type = 0: sono nel caso in cui l'oggetto da mostrare deve essere caricato via AsyncTask
         */
        if (type == 0) {
            imageView.setImageResource(R.drawable.ic_favorite_black_24dp)

            //Recupero l'ID dell'oggetto Venue e lancio l'AsyncTask
            val id : String? = AllVenue.ITEMS[position].id

            LoadRealPosition(id).execute()

            val fab : FloatingActionButton = findViewById(R.id.fab)

            //Condivisione del luogo con foto (se presente) e nome della località
            fab.setOnClickListener{
                Log.d("call","Premuto share")
                val intent = Intent()
                intent.action = Intent.ACTION_SEND_MULTIPLE
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                val drawable = (viewPager.getChildAt(0) as ImageView).drawable
                if (drawable != null) {
                    val bitmap = drawable.toBitmap()
                    val cache : File? = applicationContext.externalCacheDir
                    val shareFile = File(cache,"share.png")
                    try {
                        val fileOutputStream = FileOutputStream(shareFile)
                        bitmap.compress(Bitmap.CompressFormat.PNG,100,fileOutputStream)
                        fileOutputStream.flush()
                        fileOutputStream.close()
                    } catch (e : IOException) {
                        Log.d("call",e.toString())
                    }
                    val uri = Uri.parse("file://$shareFile")
                    intent.putExtra(Intent.EXTRA_STREAM,uri)
                    intent.type = "*/*"
                    if (address != null) {
                        intent.putExtra(Intent.EXTRA_TEXT, "${venue.name}\nAddress: $address")
                    }
                    else {
                        intent.putExtra(Intent.EXTRA_TEXT, "${venue.name}\nAddress: ${addressText.text}")
                    }
                }
                else {
                    if (address != null) {
                        intent.putExtra(Intent.EXTRA_TEXT, "${venue.name}\nAddress: $address")
                    }
                    else {
                        intent.putExtra(Intent.EXTRA_TEXT, "${venue.name}\nAddress: ${addressText.text}")
                    }
                    intent.type = "text/plain"
                }
                startActivity(Intent.createChooser(intent,resources.getString(R.string.share_venue)))
            }

            /**
             * Inizializzazione del sintetizzatore locale e riproduzione della descrizione
             * Cerco anche il Locale della zona in cui mi trovo per riprodurlo in modo corretto
             */
            playDescription.setOnClickListener {
                if (description.text != resources.getString(R.string.not_available)) {
                    tts = TextToSpeech(this, TextToSpeech.OnInitListener {
                        val locale = JSONObject(venue.location!!).optString("cc")
                        val voices = tts?.voices
                        lateinit var local : Locale
                        voices?.forEach {
                            if (it.locale.country == locale) {
                                local = it.locale
                            }
                        }
                        Log.d("call","${local.country} && ${local.displayName + local.displayCountry}")
                        val result = tts?.setLanguage(local)
                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            Log.d("call", "This Language is not supported")
                        } else {
                            tts?.speak(venue.description, TextToSpeech.QUEUE_FLUSH, null,"null")
                        }
                    })
                }
            }

            /**
             * Aggiunta della localita al database: creo un nuovo oggetto di tipo ContentValues, inserisco
             * tutte le info della località che ho caricato via AsyncTask precedentemente e chiamo la insert
             * del mio ContentProvider con la corrispondente URI.
             * Il Salvataggio avviene via coroutine perché non bisogna mai eseguire task del genere sulla UI
             */
            imageView.setOnClickListener {
                //SALVARE IN DB
                val contentValues = ContentValues()
                contentValues.put(Venue.COLUMN_VENUE_ID,venue.venueId)
                contentValues.put(Venue.COLUMN_NAME,venue.name)
                contentValues.put(Venue.COLUMN_LOCATION,venue.location)
                contentValues.put(Venue.COLUMN_RATING,venue.rating)
                contentValues.put(Venue.COLUMN_PHOTOS,venue.photos)
                contentValues.put(Venue.COLUMN_DESCRIPTION,venue.description)
                contentValues.put(Venue.COLUMN_TIPS,venue.tips)
                contentValues.put(Venue.COLUMN_LISTED,venue.listed)
                contentValues.put(Venue.COLUMN_HOURS,venue.hours)
                contentValues.put(Venue.COLUMN_DEFAULT_HOURS,venue.defaultHours)
                contentValues.put(Venue.COLUMN_ATTRIBUTES,venue.attributes)
                contentValues.put(Venue.COLUMN_BEST_PHOTO,venue.bestPhoto)
                GlobalScope.launch {
                    withContext(Dispatchers.IO) {
                        contentResolver.insert(MyContentProvider.URI_VENUE,contentValues)
                    }
                }
                Toast.makeText(applicationContext,resources.getString(R.string.item_saved),Toast.LENGTH_SHORT).show()
                val intent = Intent()
                //UNO PER SALVARE, ZERO NIENTE
                intent.putExtra("saved",1)
                setResult(Activity.RESULT_OK,intent)
            }

        }
        /**
         * Type = 1: sono nel caso in cui l'oggetto venue è gia presente nel database e lo apro per vedere informazioni piu
         * dettagliate
         */
        if (type == 1) {
            imageView.setImageResource(R.drawable.ic_delete_black_24dp)
            val venue : Venue? = intent.getParcelableExtra("venue")
            supportActionBar?.title = venue?.name

            val location : JSONObject? = JSONObject(venue?.location!!)
            val latitude = location?.optDouble("lat")
            val longitude = location?.optDouble("lng")

            GlobalScope.launch{
                withContext(Dispatchers.IO) {
                    getAddressFromLatLng(latitude!!,longitude!!)
                }
            }

            val stringArray : ArrayList<String> = ArrayList()

            //Serie di try-catch per prendere le informazioni salvate in formato JSON, mi proteggo da eventuali JSONException
            try {
                val array : JSONArray? = JSONObject(venue.photos!!).optJSONArray("groups")
                if (array != null) {
                    try {
                        val jsonObj = array.optJSONObject(0)?.optJSONArray("items")?.optJSONObject(0)
                        val url1 : String = jsonObj?.optString("prefix") +
                                jsonObj?.optInt("width").toString() +
                                "x" + jsonObj?.optInt("height").toString() +
                                jsonObj?.optString("suffix")

                        stringArray.add(url1)

                    } catch (e : JSONException) {
                        Log.d("call",e.toString())
                    }
                    try {
                        val jsonObj2 = array.optJSONObject(0)?.optJSONArray("items")?.optJSONObject(1)
                        val url2 : String = jsonObj2?.optString("prefix") +
                                jsonObj2?.optInt("width").toString() +
                                "x" + jsonObj2?.optInt("height").toString() +
                                jsonObj2?.optString("suffix")

                        stringArray.add(url2)

                    } catch (e1 : JSONException) {
                        Log.d("call",e1.toString())
                    }

                    viewPager = findViewById(R.id.view_pager)
                    viewPager.adapter = ImageAdapter(stringArray, venue.imageBytes!!)
                }
                else {
                    stringArray.add("missing")
                    viewPager = findViewById(R.id.view_pager)
                    viewPager.adapter = ImageAdapter(stringArray, venue.imageBytes!!)
                }
            } catch (e : JSONException) {
                Log.d("call",e.toString())
            }

            try {
                addressText.text = JSONObject(venue.location!!).optString("address")
            } catch (e1 : JSONException) {
                addressText.text = resources.getString(R.string.not_available)
            }
            try {
                hoursText.text = JSONObject(venue.defaultHours!!).optJSONArray("timeframes")?.optJSONObject(0)?.optString("days")
            }
            catch (e : JSONException) {
                hoursText.text = resources.getString(R.string.not_available)
            }
            if (addressText.text == null || addressText.text.isEmpty()) {
                addressText.text = resources.getString(R.string.not_available)
            }
            if (hoursText.text == null || hoursText.text.isEmpty()) {
                hoursText.text = resources.getString(R.string.not_available)
            }
            description.text = venue.description
            if (description.text == null || description.text.isEmpty()) {
                description.text = resources.getString(R.string.not_available)
            }

            val fab : FloatingActionButton = findViewById(R.id.fab)

            //Condivisione come nel caso precedente
            fab.setOnClickListener{
                Log.d("call","Premuto share")
                val intent = Intent()
                intent.action = Intent.ACTION_SEND_MULTIPLE
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                val drawable = (viewPager.getChildAt(0) as ImageView).drawable
                if (drawable != null && drawable != resources.getDrawable(R.drawable.ic_broken_image_black_24dp,null)) {
                    val bitmap = drawable.toBitmap()
                    val cache : File? = applicationContext.externalCacheDir
                    val shareFile = File(cache,"share.png")
                    try {
                        val fileOutputStream = FileOutputStream(shareFile)
                        bitmap.compress(Bitmap.CompressFormat.PNG,100,fileOutputStream)
                        fileOutputStream.flush()
                        fileOutputStream.close()
                    } catch (e : IOException) {
                        Log.d("call",e.toString())
                    }
                    val uri = Uri.parse("file://$shareFile")
                    intent.putExtra(Intent.EXTRA_STREAM,uri)
                    intent.type = "*/*"
                    if (address != null) {
                        intent.putExtra(Intent.EXTRA_TEXT, "${venue.name}\nAddress: $address")
                    }
                    else {
                        intent.putExtra(Intent.EXTRA_TEXT, "${venue.name}\nAddress: ${addressText.text}")
                    }
                }
                else {
                    if (address != null) {
                        intent.putExtra(Intent.EXTRA_TEXT, "${venue.name}\nAddress: $address")
                    }
                    else {
                        intent.putExtra(Intent.EXTRA_TEXT, "${venue.name}\nAddress: ${addressText.text}")
                    }
                    intent.type = "text/plain"
                }
                startActivity(Intent.createChooser(intent,resources.getString(R.string.share_venue)))
            }

            /**
             * Rimozione di una località dal database: la rimozione come per l'aggiunta viene effettuata
             * in una coroutine di tipo IO, utile per operazioni su database
             */
            imageView.setOnClickListener {

                GlobalScope.launch(Dispatchers.IO) {
                    asyncDelete(venue.ID.toString())
                }

                Toast.makeText(applicationContext,resources.getString(R.string.item_deleted),Toast.LENGTH_SHORT).show()
                val intent = Intent()
                intent.putExtra("saved",3)
                setResult(Activity.RESULT_OK,intent)
            }

            //Sintetizzatore vocale come nel caso precedente
            playDescription.setOnClickListener {
                if (description.text != resources.getString(R.string.not_available)) {
                    tts = TextToSpeech(this, TextToSpeech.OnInitListener {
                        val locale = JSONObject(venue.location!!).optString("cc")
                        val voices = tts?.voices
                        lateinit var local : Locale
                        voices?.forEach {
                            if (it.locale.country == locale) {
                                local = it.locale
                            }
                        }
                        Log.d("call","${local.country} && ${local.displayName + local.displayCountry}")
                        val result = if (local.country == "US") {
                            tts?.setLanguage(Locale.US)
                        } else {
                            tts?.setLanguage(local)
                        }
                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            Log.d("call", "This Language is not supported")
                        } else {
                            tts?.speak(venue.description, TextToSpeech.QUEUE_FLUSH, null,"null")
                        }
                    })
                }
            }


        }


    }

    /**
     * Blocco il sintettizatore vocale in caso di uscita dall'activity prima della fine
     */
    override fun onPause() {
        if (tts != null) {
            if (tts?.isSpeaking!!) {
                tts?.stop()
            }
        }
        super.onPause()
    }


    /**
     * LoadRealPosition: classe che eredita da AsyncTask, viene chiamato nel caso di Type = 0 quando la località deve
     * essere richiesta all'API.
     * Nella doInBackground viene stabilita la connessione e vengono scaricati i dati, questi ultimi vengono poi passati alla onPostExecute
     * che assegna i campi dell'oggetto globale venue
     *
     * @param id identificatore (stringa) per recuperare l'oggetto via API
     */
    @SuppressLint("StaticFieldLeak")
    inner class LoadRealPosition(var id : String?) :
        AsyncTask<String?, String?, String?>(){

        //private var url 

        override fun doInBackground(vararg params: String?): String? {
            val httpClient = url.openConnection() as HttpURLConnection
            if (httpClient.responseCode == HttpURLConnection.HTTP_OK) {
                try {
                    val stream = BufferedInputStream(httpClient.inputStream)
                    val bufferedReader = BufferedReader(InputStreamReader(stream))
                    val stringBuilder = StringBuilder()
                    bufferedReader.forEachLine { stringBuilder.append(it) }
                    return stringBuilder.toString()
                } catch (e : Exception) {
                    Log.d("call",e.toString())
                }
                finally {
                    httpClient.disconnect()
                }
            }
            return null
        }

        override fun onPostExecute(result: String?) {
            if (result != null) {
                try {
                    val jsonObject = JSONObject(result)
                    val object2 = jsonObject.getJSONObject("response").getJSONObject("venue")
                    with(venue) {
                        venueId = object2.optString("id")
                        name = object2.optString("name")
                        location = object2.optString("location")
                        rating = if (object2.optDouble("rating").isNaN()) {
                            0.0
                        } else {
                            object2.optDouble("rating")
                        }
                        photos = object2.optString("photos")
                        description = object2.optString("description")
                        tips = object2.optString("tips")
                        listed = object2.optString("listed")
                        hours = object2.optString("hours")
                        defaultHours = object2.optString("defaultHours")
                        attributes = object2.optString("attributes")
                        bestPhoto = object2.optString("bestPhoto")
                    }

                    supportActionBar?.title = venue.name

                    val stringArray : ArrayList<String> = ArrayList()

                    try {
                        val array : JSONArray? = JSONObject(venue.photos!!).optJSONArray("groups")
                        if (array != null) {
                            try {
                                val jsonObj = array.optJSONObject(0)?.optJSONArray("items")?.optJSONObject(0)
                                val url1 : String = jsonObj?.optString("prefix") +
                                        jsonObj?.optInt("width").toString() +
                                        "x" + jsonObj?.optInt("height").toString() +
                                        jsonObj?.optString("suffix")

                                stringArray.add(url1)

                            } catch (e : JSONException) {
                                Log.d("call",e.toString())
                            }
                            try {
                                val jsonObj2 = array.optJSONObject(0)?.optJSONArray("items")?.optJSONObject(1)
                                val url2 : String = jsonObj2?.optString("prefix") +
                                        jsonObj2?.optInt("width").toString() +
                                        "x" + jsonObj2?.optInt("height").toString() +
                                        jsonObj2?.optString("suffix")

                                stringArray.add(url2)

                            } catch (e1 : JSONException) {
                                Log.d("call",e1.toString())
                            }

                            viewPager = findViewById(R.id.view_pager)
                            viewPager.adapter = ImageAdapter(stringArray,null)
                        }
                        else {
                            stringArray.add("missing")
                            viewPager = findViewById(R.id.view_pager)
                            viewPager.adapter = ImageAdapter(stringArray,null)
                        }
                    } catch (e : JSONException) {
                        Log.d("call",e.toString())
                    }
                } catch(e : JSONException) {
                }


                try {
                    addressText.text = JSONObject(venue.location!!).optString("address")
                }
                catch (e : JSONException) {
                    addressText.text = resources.getString(R.string.not_available)
                }
                try {
                    hoursText.text = JSONObject(venue.defaultHours!!).optJSONArray("timeframes")?.optJSONObject(0)?.optString("days")
                }
                catch (e : JSONException) {
                    hoursText.text = resources.getString(R.string.not_available)
                }
                if (addressText.text == null || addressText.text.isEmpty()) {
                    addressText.text = resources.getString(R.string.not_available)
                }
                if (hoursText.text == null || hoursText.text.isEmpty()) {
                    hoursText.text = resources.getString(R.string.not_available)
                }
                description.text = venue.description
                if (description.text == null || description.text.isEmpty()) {
                    description.text = resources.getString(R.string.not_available)
                }

                val location : JSONObject? = JSONObject(venue.location!!)
                val latitude = location?.optDouble("lat")
                val longitude = location?.optDouble("lng")

                GlobalScope.launch{
                    withContext(Dispatchers.Default) {
                        getAddressFromLatLng(latitude!!,longitude!!)
                    }
                }
            }
            super.onPostExecute(result)
        }

    }

    /**
     * Utilizzo una coroutine di tipo IO per chiedere al geocoder l'indirizzo alla data latitudine e longitudine
     */
    private suspend fun getAddressFromLatLng(latitude : Double?, longitude : Double?) = withContext(Dispatchers.IO) {
        val addressList = geocoder.getFromLocation(latitude!!, longitude!!, 1)
        address = addressList[0].getAddressLine(0)
        Log.d("call","Coroutine geocoder terminata")
    }

    /**
     * Coroutine per eliminare una località dal database
     */
    private suspend fun asyncDelete(id : String) = withContext(Dispatchers.IO) {
        contentResolver.delete(MyContentProvider.URI_VENUE.buildUpon().appendPath(id).build(),null,null)
        Log.d("call","Coroutine delete terminata")
    }


}
