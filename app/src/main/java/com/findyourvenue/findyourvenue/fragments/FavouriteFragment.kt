package com.findyourvenue.findyourvenue.fragments

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.cardview.widget.CardView
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.findyourvenue.findyourvenue.MainActivity
import com.findyourvenue.findyourvenue.R
import com.findyourvenue.findyourvenue.activity.ActivityVenue
import com.findyourvenue.findyourvenue.data.Venue
import com.findyourvenue.findyourvenue.provider.MyContentProvider
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Runnable

/**
 * Classe FavouriteFragment: Fragment che viene creato quando si vogliono vedere le località
 * memorizzate nel database
 */
class FavouriteFragment : Fragment() {

    //RecyclerView per visualizzare gli elementi
    private lateinit var recyclerView : RecyclerView

    //Adapter per la recyclerView
    private lateinit var venueAdapter: VenueAdapter

    //searchView per la ricerca tra gli elementi della recyclerView
    private lateinit var searchView : SearchView

    //HashMap per memorizzare gli indirizzi trovati dal Geocoder
    var listAddress : HashMap<Int,String> = HashMap()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val rootView : View = inflater.inflate(R.layout.fragment_favourite, container, false)

        retainInstance = true

        activity?.title = resources.getString(R.string.favourites)

        recyclerView = rootView.findViewById(R.id.recycler_view_favourite)

        recyclerView.layoutManager = LinearLayoutManager(this.context)

        venueAdapter = VenueAdapter()

        recyclerView.adapter = venueAdapter

        venueAdapter.setVenues(MainActivity.cursor)

        activity?.actionBar?.customView?.setOnClickListener{
            recyclerView.scrollToPosition(0)
        }

        setHasOptionsMenu(true)

        return rootView
    }

    /**
     * Istanza di LoaderManager.LoaderCallbacks, utilizzata per caricare le località
     * dal database quando viene modificato all'interno di questo fragment.
     * Nella OnCreateLoader se args != null allora sto utilizzando la barra di ricerca, quindi
     * filtro i risultati di conseguenza in base alla query
     */
     private val mLoaderCallbacks: LoaderManager.LoaderCallbacks<Cursor?> =
        object : LoaderManager.LoaderCallbacks<Cursor?> {
            override fun onCreateLoader(
                id: Int,
                args: Bundle?
            ): Loader<Cursor?> {
                Log.d("call","Creo il loader")
                if (args == null) {
                    return CursorLoader(
                        context?.applicationContext!!,
                        MyContentProvider.URI_VENUE, null,
                        null, null, null
                    )
                }
                else {
                    Log.d("call","Caso di query dalla SearchView")
                    val query = args.getString("name","null")
                    if (query != "null" && query.isNotEmpty()) {
                        val array = Array(1){"%$query%"}
                        return CursorLoader(
                            context?.applicationContext!!,
                            MyContentProvider.URI_VENUE, null,
                            Venue.COLUMN_NAME + " LIKE ?", array, null
                        )
                    }
                }
                return CursorLoader(
                    context?.applicationContext!!,
                    MyContentProvider.URI_VENUE, null,
                    null, null, null
                )
            }

            override fun onLoadFinished(
                loader: Loader<Cursor?>,
                data: Cursor?
            ) {
                Log.d("call","Aggiorno direttamente il Cursor collegato alla RecyclerView")
                venueAdapter.setVenues(data)
            }

            override fun onLoaderReset(loader: Loader<Cursor?>) {
                Log.d("call","Il loader è stato resettato")
                venueAdapter.setVenues(null)
            }
        }

    /**
     * Override di OnCreateOptionsMenu per abilitare la barra di ricerca nella AppBar.
     * Le ricerca viene filtrata sia all'invio che durante la scrittura
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fav_menu,menu)
        val item : MenuItem = menu.findItem(R.id.search)!!
        searchView = item.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val bundle = Bundle()
                bundle.putString("name",query)
                Log.d("call","on query submit")
                LoaderManager.getInstance(this@FavouriteFragment).restartLoader(1,bundle,mLoaderCallbacks)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val bundle = Bundle()
                bundle.putString("name",newText)
                Log.d("call","on text change")
                LoaderManager.getInstance(this@FavouriteFragment).restartLoader(1,bundle,mLoaderCallbacks)
                return true
            }

        })
        searchView.setOnCloseListener {
            LoaderManager.getInstance(this@FavouriteFragment).restartLoader(1,null,mLoaderCallbacks)
            false
        }
        //return super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.search -> {
                searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        val bundle = Bundle()
                        bundle.putString("name",query)
                        Log.d("call","on text submit")
                        LoaderManager.getInstance(this@FavouriteFragment).initLoader(1,bundle,mLoaderCallbacks)
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        if (newText?.isNotEmpty()!!) {
                            val bundle = Bundle()
                            bundle.putString("name",newText)
                            Log.d("call","on text change")
                            LoaderManager.getInstance(this@FavouriteFragment).initLoader(1,bundle,mLoaderCallbacks)
                            return true
                        }
                        return true
                    }

                })
                return true
            }
        }
        return false
    }


    /**
     * Override di OnActivityResult per riavviare il LoaderManger in caso di località
     * eliminata dal database o nuova foto aggiunta a una località
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == VENUE_TO_DELETE) {
            if (resultCode == Activity.RESULT_OK) {
                if (data?.extras?.get("saved") == 3)
                    LoaderManager.getInstance(this@FavouriteFragment).initLoader(1,null,mLoaderCallbacks)
            }
        }
        if (requestCode == PICTURE_SAVED) {
            if (resultCode == Activity.RESULT_OK) {
                LoaderManager.getInstance(this@FavouriteFragment).restartLoader(1,null,mLoaderCallbacks)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Classe VenueAdapter: classe utilizzata per l'adapter della recyclerView
     */
    inner class VenueAdapter : RecyclerView.Adapter<VenueAdapter.ViewHolder>() {

        private var mCursor: Cursor? = null


        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): VenueAdapter.ViewHolder {
            val view : View = LayoutInflater.from(parent.context).inflate(R.layout.item_list2,parent,false)
            return ViewHolder(view)
        }

        /**
         * Con il cursor vengono recuperati i dati e vengono messi nella UI
         */
        @InternalCoroutinesApi
        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int
        ) {
            try {
                if (mCursor?.moveToPosition(position)!!) {

                    holder.title.text = mCursor!!.getString(
                        mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_NAME))

                    val location = mCursor!!.getString(
                        mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_LOCATION))


                    val obj : JSONObject? = JSONObject(location)

                    val latitude = obj?.optDouble("lat")
                    val longitude = obj?.optDouble("lng")

                    //Utilizzo una coroutine di tipo IO per caricare l'indirizzo della località con il geocoder
                    GlobalScope.launch{
                        withContext(Dispatchers.IO) {
                            getAddressFromLatLng(latitude!!,longitude!!,position)
                        }
                    }

                    holder.address.text = obj?.optString("address")

                    var text : String? = null
                    if (mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_DEFAULT_HOURS)).isEmpty()) {
                        text = null
                    } else {
                        try {
                            val hours : JSONObject? = JSONObject(mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_DEFAULT_HOURS)))
                            val text1 = hours?.optJSONArray("timeFrames")?.optJSONObject(0)?.optString("days")
                            if (text1 != null) {
                                val text2 = hours.optJSONArray("timeframes")?.optJSONObject(0)?.optJSONArray("open")?.optJSONObject(0)?.optString("renderedTime")
                                text = if (text2 != null) {
                                    "$text1:$text2"
                                } else text1
                            }
                            else {
                                val text2 = hours?.optJSONArray("timeframes")?.optJSONObject(0)?.optJSONArray("open")?.optJSONObject(0)?.optString("renderedTime")
                                if (text2 != null) {
                                    text = text2
                                }
                            }
                        } catch (e : JSONException) {
                            Log.d("call","Exception")
                        }

                    }

                    if (text == null || text.isEmpty() || text == "null : null") {
                        text = resources.getString(R.string.not_available)
                    }
                    holder.hours.text = text

                    if (holder.address.text.isEmpty() || holder.address.text == null) {
                        holder.address.text = resources.getString(R.string.not_available)
                    }

                    try {
                        val photos2: JSONObject? = JSONObject(mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_PHOTOS)))
                        val array : JSONArray? = photos2?.optJSONArray("groups")
                        if (array != null && array.length() > 0) {
                            val jsonObj = array.optJSONObject(0)?.optJSONArray("items")?.optJSONObject(0)
                            val url : String = jsonObj?.optString("prefix") +
                                    jsonObj?.optInt("width").toString() +
                                    "x" + jsonObj?.optInt("height").toString() +
                                    jsonObj?.optString("suffix")

                            val color: Int = Color.rgb(192, 192, 192)

                            val gradientDrawable = GradientDrawable()
                            gradientDrawable.shape = GradientDrawable.RECTANGLE
                            gradientDrawable.setColor(color)

                            Picasso.get().load(Uri.parse(url))
                                .placeholder(gradientDrawable)
                                .error(R.drawable.ic_broken_image_black_24dp)
                                .into(holder.imageView)
                        }

                    } catch (e : JSONException) {
                        Log.d("call","Exception")
                    }

                    /**
                     * Lancio ActivityVenue per la visualizzazione estesa della località, la località viene
                     * Parcelizzata e salvata negli extra dell'intent con type = 1, per indicaare che si tratta
                     * di un elemento salvato nel database
                     */
                    holder.cardView.setOnClickListener{
                        if (mCursor?.moveToPosition(position)!!) {
                            val intent = Intent(activity?.applicationContext!!, ActivityVenue::class.java)
                            val venue = Venue()
                            with(venue) {
                                ID = mCursor!!.getLong(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_ID))
                                venueId = mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_VENUE_ID))
                                name = mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_NAME))
                                //location = mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_LOCATION))
                                rating = mCursor!!.getDouble(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_RATING))
                                photos = mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_PHOTOS))
                                description = mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_DESCRIPTION))
                                tips = mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_TIPS))
                                listed = mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_LISTED))
                                hours = mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_HOURS))
                                defaultHours = mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_DEFAULT_HOURS))
                                attributes = mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_ATTRIBUTES))
                                bestPhoto = mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_BEST_PHOTO))
                                imageBytes = mCursor!!.getBlob(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_SAVED_PICS))
                            }
                            venue.location = mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_LOCATION))
                            intent.putExtra("venue",venue)
                            intent.putExtra("position",position)
                            intent.putExtra("type",1)
                            startActivityForResult(intent, VENUE_TO_DELETE)
                        }
                    }

                    /**
                     * Come prima lancio una coroutine per effettuare la chiamata
                     * di delete al ContentResolver in un thread che non sia UI, al termine viene chiesto
                     * al LoaderManager di ripartire per ricaricare il Cursor nuovo. La coroutine di cancellazione viene chiamata
                     * all'interno di un Dispatchers.IO per il caricamento asincrono, al termine di questa viene chiamata un'altra
                     * coroutine di tipo Main per far ripartire il LoaderManager. Bisogna utilizzare una coroutine Main per il LoaderManager
                     * poichè non puo' essere invocato su un thread che non sia quello della UI
                     */
                    holder.deleteButton.setOnClickListener {

                        if (mCursor?.moveToPosition(position)!!) {

                            Log.d("call","Premuto delete")
                            GlobalScope.launch(Dispatchers.IO) {
                                asyncDelete(mCursor!!.getLong(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_ID)).toString())
                                withContext(Dispatchers.Main) {
                                    LoaderManager.getInstance(this@FavouriteFragment).initLoader(1,null,mLoaderCallbacks)
                                }
                            }
                            Toast.makeText(context,resources.getString(R.string.item_deleted), Toast.LENGTH_SHORT).show()
                        }
                    }

                    /**
                     * Come nei casi precedenti per la condivisione viene mandato il nome con una foto
                     */
                    holder.shareButton.setOnClickListener{
                        if (mCursor?.moveToPosition(position)!!) {
                            Log.d("call","Premuto share")
                            val intent = Intent()
                            intent.action = Intent.ACTION_SEND_MULTIPLE
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            val drawable = holder.imageView.drawable
                            if (drawable != null && drawable != resources.getDrawable(R.drawable.ic_broken_image_black_24dp,null)) {
                                val bitmap = drawable.toBitmap()
                                val cache : File? = activity?.applicationContext?.externalCacheDir
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
                                if (listAddress[position] != null) {
                                    intent.putExtra(Intent.EXTRA_TEXT, "${mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_NAME))}\nAddress: ${listAddress[position]}")
                                }
                                else intent.putExtra(Intent.EXTRA_TEXT, "${mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_NAME))}\nAddress: ${holder.address.text}")
                            }
                            else {
                                if (listAddress[position] != null) {
                                    intent.putExtra(Intent.EXTRA_TEXT, "${mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_NAME))}\nAddress: ${listAddress[position]}")
                                }
                                else intent.putExtra(Intent.EXTRA_TEXT, "${mCursor!!.getString(mCursor!!.getColumnIndexOrThrow(Venue.COLUMN_NAME))}\nAddress: ${holder.address.text}")
                                intent.type = "text/plain"
                            }
                            startActivity(Intent.createChooser(intent,resources.getString(R.string.share_venue)))
                        }
                    }
                }
            } catch (e : Exception) {
                Log.d("call",e.toString())
            }
        }

        override fun getItemCount(): Int {
            return if (mCursor == null) 0 else mCursor!!.count
        }

        fun setVenues(cursor: Cursor?) {
            mCursor = cursor
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView : View) : RecyclerView.ViewHolder(itemView) {
            val imageView : ImageView = itemView.findViewById(R.id.venue_photo)
            val title : TextView = itemView.findViewById(R.id.venue_title)
            val hours : TextView = itemView.findViewById(R.id.venue_hours)
            val address : TextView = itemView.findViewById(R.id.venue_address)
            val deleteButton : ImageButton = itemView.findViewById(R.id.delete_button)
            val shareButton : ImageButton = itemView.findViewById(R.id.share_button)
            val cardView : CardView = itemView.findViewById(R.id.card_view)
        }
    }

    /**
     * Utilizzo anche qui una Coroutine di tipo IO (poichè utilizzo il metodo getFromLocation di geocoder) e memorizzo
     * l'indirizzo trovato in listAddress con chiave la posizione e valore l'indirizzo vero e proprio
     */
    private suspend fun getAddressFromLatLng(latitude : Double?, longitude : Double?,position: Int) = withContext(Dispatchers.IO) {
        val addressList = MainActivity.geocoder.getFromLocation(latitude!!, longitude!!, 1)
        listAddress[position] = addressList[0].getAddressLine(0)
        Log.d("call","Coroutine geocoder terminata")
    }

    /**
     * Coroutine per eliminare una località dal database
     */
    private suspend fun asyncDelete(id : String) = withContext(Dispatchers.IO) {
        activity?.contentResolver?.delete(MyContentProvider.URI_VENUE.buildUpon().appendPath(id).build(),null,null)
        Log.d("call","Coroutine delete terminata")
    }

    /**
     * Companion Object: constanti statiche usate nella OnActivityResult per i codici di risposta
     */
    companion object {
        const val VENUE_TO_DELETE = 2011

        const val PICTURE_SAVED = 2002
    }


}
