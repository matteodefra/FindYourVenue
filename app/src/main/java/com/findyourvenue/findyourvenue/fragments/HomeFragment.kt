package com.findyourvenue.findyourvenue.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Point
import android.location.Location
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.findyourvenue.findyourvenue.MainActivity
import com.findyourvenue.findyourvenue.R
import com.findyourvenue.findyourvenue.activity.ActivityVenue
import com.findyourvenue.findyourvenue.data.AllVenue
import com.findyourvenue.findyourvenue.data.VenueMain
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.appbar.AppBarLayout
import com.google.maps.android.PolyUtil
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_home.*
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import kotlin.math.roundToInt


/**
 * A simple [Fragment] subclass.
 */
class HomeFragment : Fragment(),OnMapReadyCallback{

    //RecyclerView per la visualizzazione dei risultati
    private lateinit var recyclerView : RecyclerView

    //VenueAdapter per l'adapter della recyclerView
    private lateinit var venueAdapter : VenueAdapter

    //ProgressBar per il caricamento delle località
    private lateinit var progressBar : ProgressBar

    private lateinit var noInternet : LinearLayout
    private lateinit var noElements : LinearLayout

    //Lista di valori LatLng per la ricerca custom in un'area
    private var latLngValues : MutableList<LatLng> = mutableListOf()

    //valore booleano per la gestione del touch sulla mappa
    private var isMapMoveable = false

    //var circle : Circle? = null

    //Oggetto poligono da disegnare in base ai valori di latLngValues
    private var polygon : Polygon? = null

    /**
     * Oggetti SupportMapFragment e GoogleMap: il primo per recuperare l'oggetto dal layout xml
     * e il secondo per la mappa vera e propria
     */
    private var mapView : SupportMapFragment? = null
    private var googleMap : GoogleMap? = null

    private var actualLatitude : Double = 0.0
    private var actualLongitude : Double = 0.0

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val rootView : View = inflater.inflate(R.layout.fragment_home, container, false)

        activity?.title = resources.getString(R.string.home_fragment)

        progressBar = rootView.findViewById(R.id.progress_bar)

        noInternet = rootView.findViewById(R.id.no_connection)

        noElements = rootView.findViewById(R.id.no_elements)

        mapView = childFragmentManager.findFragmentById(R.id.map_view) as SupportMapFragment

        mapView?.onCreate(savedInstanceState)

        mapView?.getMapAsync(this)

        try {
            MapsInitializer.initialize(activity?.applicationContext)
        } catch (e : Exception) {
            Log.d("call",e.toString())
        }

        /**
         * FrameLayout è un layout trasparente, l'ho inserito
         * dopo la MapView in modo da gestire il touch per la ricerca customizzata
         */
        val frameLayout : FrameLayout = rootView.findViewById(R.id.map_container)
        val buttonChange : ImageButton = rootView.findViewById(R.id.draw_polygon)
        val buttonConfirm : ImageButton = rootView.findViewById(R.id.selection_done)
        val buttonClear : ImageButton = rootView.findViewById(R.id.clear_map)

        /**
         * buttonChange: utilizzato per invertire tra il touch di default della mappa
         * e il touch in caso di ricerca con il frameLayout sottostante
         */
        buttonChange.setOnClickListener{
            isMapMoveable = true
            latLngValues.clear()
            googleMap?.clear()
        }

        /**
         * buttonConfirm: bottone per confermare la ricerca dopo aver tracciato il poligono.
         * Viene recuperato il centro del poligono e viene fatto partire un AsyncTask con
         * una ricerca delle località di raggio centro del poligono - punto piu lontano del poligono
         * (in modo da coprire tutta la zona) e vengono rimosse le località che non rientrano nella
         * zona
         */
        buttonConfirm.setOnClickListener{
            if (isMapMoveable) {
                isMapMoveable = false
                if (polygon != null) {
                    val center = getPolygonCenterPoint()
                    val location = Location("")
                    location.latitude = center.latitude
                    location.longitude = center.longitude
                    var distance = 0F
                    for (i in 0 until polygon?.points?.size!!) {
                        val loc = Location("")
                        loc.latitude = polygon?.points?.get(i)?.latitude!!
                        loc.longitude = polygon?.points?.get(i)?.longitude!!
                        val partial = location.distanceTo(loc)
                        if (partial > distance) distance = partial
                    }
                    googleMap?.clear()
                    val rectOptions = PolygonOptions()
                    rectOptions.addAll(latLngValues)
                    rectOptions.fillColor(0x220000FF)
                    rectOptions.strokeWidth(0F)
                    polygon = googleMap?.addPolygon(rectOptions)
                    LoadItemsPositionBased(center,distance).execute()
                    AllVenue.ITEMS.clear()
                }
            }
        }

        /**
         * buttonClear: rimuove tutti i marker e poligoni dalla mappa e risetta isMapMoveable al
         * touch di default della mappa
         */
        buttonClear.setOnClickListener{
            googleMap?.clear()
            latLngValues.clear()
            isMapMoveable = false
        }

        /**
         * frameLayout: implementa la logica del draw sulla mappa: Override del metodo onTouch per gestire
         * la pressione dell'utente. Recupero le posizioni x e y del touch dell'utente e le converto
         * in un oggetto di tipo LatLng, che vado ad aggiungere alla lista degli oggetti. Appena l'utente posa
         * il dito sullo schermo le posizioni vengono acquisite e il poligono viene disegnato via via che si sposta
         * Al momento del rilascio del dito il poligono rimane sullo schermo e alla conferma della selezione
         * viene fatto partire l'AsyncTask per caricare i dati
         */
        frameLayout.setOnTouchListener(object : View.OnTouchListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (!isMapMoveable) return false
                Log.d("call","Intercetto il movimento del dito sulla Mappa")
                val x: Float = event!!.x
                val y: Float = event.y

                val xInt = x.roundToInt()
                val yInt = y.roundToInt()

                val xyPoints = Point(xInt,yInt)

                val latLng: LatLng = googleMap?.projection!!.fromScreenLocation(xyPoints)
                val latitude = latLng.latitude

                val longitude = latLng.longitude

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // finger touches the screen
                        latLngValues.add(LatLng(latitude, longitude))
                        // finger moves on the screen
                        latLngValues.add(LatLng(latitude, longitude))
                        // finger leaves the screen
                        drawMap()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        latLngValues.add(LatLng(latitude, longitude))
                        drawMap()
                    }
                    MotionEvent.ACTION_UP -> drawMap()
                }

                return isMapMoveable
            }

        })

        //Registro un listener per le SharedPreferences, in modo che al momento in cui l'utente cambia posizione viene aggiornata la mappa
        activity?.getSharedPreferences(MainActivity.PREFS_NAME,Context.MODE_PRIVATE)?.registerOnSharedPreferenceChangeListener(listener)

        /**
         * Override del DragCallBack di AppBarLayout. In questo modo l'utente puo muoversi liberamente sulla mappa e al momento
         * di uno swipeup nella zona della recyclerView l'AppBarLayout contenente la mappa viene nascosto
         */
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            val appBarLayout : AppBarLayout = rootView.findViewById(R.id.app_bar_layout)
            val params : CoordinatorLayout.LayoutParams = appBarLayout.layoutParams as CoordinatorLayout.LayoutParams
            val behavior : AppBarLayout.Behavior = AppBarLayout.Behavior()
            behavior.setDragCallback(object : AppBarLayout.Behavior.DragCallback() {
                override fun canDrag(p0: AppBarLayout): Boolean {
                    return false
                }

            })
            params.behavior = behavior
        }

        recyclerView = rootView.findViewById(R.id.recycler_view_home)

        venueAdapter = VenueAdapter()

        recyclerView.layoutManager = StaggeredGridLayoutManager(2,StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = venueAdapter

        activity?.actionBar?.customView?.setOnClickListener{
            recyclerView.scrollToPosition(0)
        }

        return rootView

    }

    /**
     * Metodo drawMap: utilizzato per disegnare il poligono
     */
    fun drawMap() {
        val rectOptions = PolygonOptions()
        rectOptions.addAll(latLngValues)
        rectOptions.fillColor(0x220000FF)
        rectOptions.strokeWidth(0F)
        polygon = googleMap?.addPolygon(rectOptions)
    }

    /**
     * Metodo getPolygonCenterPoint: utilizza LatLngBounds.Builder per
     * recuperare il centro del poligono dai valori LatLng disegnati dall'utente
     *
     * @return un LatLng con le coordinate del centro del poligono
     */
    private fun getPolygonCenterPoint(): LatLng {
        val centerLatLng: LatLng?
        val builder = LatLngBounds.Builder()
        for (i in 0 until polygon?.points?.size!!) {
            builder.include(polygon?.points?.get(i))
        }
        val bounds: LatLngBounds = builder.build()
        centerLatLng = bounds.center
        return centerLatLng
    }

    /**
     * Override di onMapClick: vecchio metodo utilizzato in precedenza. Invece di disegnare la propria zona di ricerca,
     * al tap della MapView veniva disegnato un cerchio rappresentante l'area di ricerca
     */
//    override fun onMapClick(p0: LatLng?) {
//        if (circle != null) {
//            var float : FloatArray = FloatArray(1)
//            Location.distanceBetween(circle!!.center.latitude,
//                circle!!.center.longitude, p0?.latitude!!,p0.longitude,float)
//            if (float[0] < 2000) {
//                return
//            }
//        }
//        googleMap?.clear()
//        var circleOptions : CircleOptions? = CircleOptions()
//            .center(LatLng(p0?.latitude!!,p0.longitude))
//            .radius(2000.0)
//                .fillColor(0x220000FF)
//            .strokeWidth(0F)
//
//        var cameraPosition : CameraPosition = CameraPosition.Builder().target(LatLng(p0.latitude,p0.longitude)).zoom(12F).build()
//        googleMap?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
//
//        LoadItemsPositionBased(p0).execute()
//        AllVenue.ITEMS.clear()
//
//        circle = googleMap?.addCircle(circleOptions)
//    }

    /**
     * listener utilizzato per ricevere eventuali cambiamenti nelle sharedPreferences. Alla modifica
     * viene aggiunto il marker sulla posizione relativa e la camera viene centrata in quella posizione
     */
    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, _ ->
            actualLatitude = sharedPreferences?.getString("Latitude","MISSING")!!.toDouble()
            actualLongitude = sharedPreferences.getString("Longitude","MISSING")!!.toDouble()
            googleMap?.clear()
            val latLng = LatLng(actualLatitude,actualLongitude)
            googleMap?.addMarker(MarkerOptions().position(latLng).title("Your Position"))

            val cameraPosition : CameraPosition = CameraPosition.Builder().target(latLng).zoom(12F).build()
            googleMap?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }

    /**
     * onMapReady: metodo chiamato quando la mapView.getMapAsync() termina l'esecuzione. Restituisce un oggetto
     * di tipo GoogleMap, vengono controllati i permessi sulla posizione e in caso positivo viene settato
     * isMyLocationEnabled, in modo da mostrare la posizione in cui si trova l'utente.
     * Inoltre come nel caso del listener delle sharedPreferences, vengono recuperati gli ultimi valori di latitudine e longitudine
     * e viene aggiunto un marker e centrata la camera
     */
    override fun onMapReady(p0: GoogleMap?) {
        googleMap = p0
        if (ContextCompat.checkSelfPermission(activity?.applicationContext!!, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted. Request for permission
//            Log.d("call", "Ho effettivamente i permessi?")
//            ActivityCompat.requestPermissions(
//                activity?.parent!!,
//                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
//                MainActivity.MY_PERMISSIONS_ACCESS_LOCATION
//            )

            // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
            // app-defined int constant. The callback method gets the
            // result of the request.
        }
        else {
            googleMap?.isMyLocationEnabled = true
        }
       // googleMap?.setOnMapClickListener(this)

        val sharedPreferences : SharedPreferences? = activity?.getSharedPreferences(MainActivity.PREFS_NAME,Context.MODE_PRIVATE)
        val latitude = sharedPreferences?.getString("Latitude","MISSING")
        val longitude = sharedPreferences?.getString("Longitude","MISSING")

        if (latitude != "MISSING" && longitude != "MISSING") {
            actualLatitude = latitude!!.toDouble()
            actualLongitude = longitude!!.toDouble()
            val latLng = LatLng(actualLatitude,actualLongitude)
            googleMap?.addMarker(MarkerOptions().position(latLng).title("Your Location"))

            val cameraPosition : CameraPosition = CameraPosition.Builder().target(latLng).zoom(12F).build()
            googleMap?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }
    }

    /**
     * Override dei metodi onResume, onPause, onDestroy e OnSaveInstanceState rispettivamente per:
     * ricaricare la mappa nella onResume, mettere in pausa la mappa nella onPause, distruggere la
     * mappa nella onDestroy e salvare lo stato della mappa nella onSaveInstanceState
     * Override anche di onLowMemory per la gestione di eventuali kill da parte del sistema, riferimento qui sotto:
     * https://developer.android.com/reference/android/app/Application.html#onLowMemory%28%29
     */
    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
        activity?.getSharedPreferences(MainActivity.PREFS_NAME,Context.MODE_PRIVATE)?.unregisterOnSharedPreferenceChangeListener(listener)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    /**
     * Classe VenueAdapter: adapter della recyclerView per mostrare le località trovate nella zona
     */
    inner class VenueAdapter : RecyclerView.Adapter<VenueAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VenueAdapter.ViewHolder {
            val view : View = LayoutInflater.from(parent.context).inflate(R.layout.item_list,parent,false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int {
            return AllVenue.ITEMS.size
        }



        override fun onBindViewHolder(holder: VenueAdapter.ViewHolder, position: Int) {
            val venueMain : VenueMain = AllVenue.ITEMS[position]
            holder.title.text = venueMain.name
            try {
                val location : JSONObject? = JSONObject(venueMain.locationJson!!)
                val latitude = location?.optDouble("lat")
                val longitude = location?.optDouble("lng")

                holder.address.text = location?.optString("address")

                if (holder.address.text == null || holder.address.text.isEmpty()) {
                    holder.address.text = resources.getString(R.string.not_available)
                }

                val latLng = LatLng(latitude!!,longitude!!)

                val markerOptions : MarkerOptions = MarkerOptions()
                    .position(latLng)
                    .title(venueMain.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))

                googleMap?.addMarker(markerOptions)


                val icon = JSONObject(venueMain.categoriesJson!!).optJSONObject("icon")
                val url = icon?.optString("prefix") + "64" + icon?.optString("suffix")

                Picasso.get().load(Uri.parse(url))
                    .placeholder(R.drawable.ic_image_black_24dp)
                    .error(R.drawable.ic_broken_image_black_24dp)
                    .into(holder.categoryIcon)

                holder.category.text = JSONObject(venueMain.categoriesJson!!).optString("name")

                if (holder.category.text == null || holder.category.text.isEmpty()) {
                    holder.category.text = resources.getString(R.string.not_available)
                }

                /**
                 * Viene lanciata ActivityVenue con type = 0, per caricare le informazioni piu dettagliate della località
                 */
                holder.cardView.setOnClickListener{
                    val intent = Intent(activity?.applicationContext!!, ActivityVenue::class.java)
                    intent.putExtra("position",position)
                    intent.putExtra("type",0)
                    startActivityForResult(intent,VENUE_TO_ADD)
                }

                /**
                 * Condivisione del nome della località
                 */
                holder.shareButton.setOnClickListener{
                    Log.d("call","Premuto share")
                    val intent = Intent()
                    intent.action = Intent.ACTION_SEND
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    intent.putExtra(Intent.EXTRA_TEXT, "${venueMain.name}: ${holder.address.text}")
                    intent.type = "text/plain"
                    startActivity(Intent.createChooser(intent,resources.getString(R.string.share_venue)))
                }
            } catch (e : JSONException) {
                Log.d("exce","$position and  $e")
            }

        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title : TextView = itemView.findViewById(R.id.venue_title)
            val address : TextView = itemView.findViewById(R.id.venue_address)
            val category : TextView = itemView.findViewById(R.id.venue_category)
            val categoryIcon : ImageView = itemView.findViewById(R.id.venue_category_icon)
            val shareButton : ImageButton = itemView.findViewById(R.id.share_button)
            val cardView : CardView = itemView.findViewById(R.id.card_view)
        }

    }

    /**
     * Classe LoadItemsPositionBased: classe che eredita da AsyncTask per il caricamento in background delle località.
     * La risposta in formato Json viene poi nella onPostExecute "parsata" e le località vengono aggiunte man mano
     * all'oggetto AllVenue.ITEMS, lista di riferimento per la recyclerView. Inoltre per ogni località trovata che rientra
     * nei limiti del poligono disegnato dall'utente viene aggiunto anche il marker all'interno della mappa
     *
     * @param latLng è la latitudine e longitudine corrispondente al centro del poligono
     * @param distance è la distanza in Float dal centro al punto piu lontano del poligono
     */
    @SuppressLint("StaticFieldLeak")
    inner class LoadItemsPositionBased(latLng: LatLng,distance : Float) :
        AsyncTask<String?, String?, String?>() {


        private var url =
            URL("https://api.foursquare.com/v2/venues/search?ll=${latLng.latitude},${latLng.longitude}&radius=${distance.toInt()}&limit=100&client_id=LILWFQDA3UBIPFWJWB2MQDCOO0FUN0RXM1LVLB5UHC04A3QN&client_secret=DIQYXL3IDNYYIRR0BQBNL35XLR3LRPGAWQZB2YIMZRI4ZAYV&v=20200430")

        override fun onPreExecute() {
            progressBar.visibility = View.VISIBLE
            noElements.visibility = View.INVISIBLE
            noInternet.visibility = View.INVISIBLE
            super.onPreExecute()
        }

        override fun doInBackground(vararg params: String?): String? {
            try {
                val httpClient = url.openConnection() as HttpURLConnection
                if (httpClient.responseCode == HttpURLConnection.HTTP_OK) {
                    try {
                        val stream = BufferedInputStream(httpClient.inputStream)
                        val bufferedReader = BufferedReader(InputStreamReader(stream))
                        val stringBuilder = StringBuilder()
                        bufferedReader.forEachLine { stringBuilder.append(it) }
                        return stringBuilder.toString()
                    } catch (e : Exception) {
                        Log.d("exce",e.toString())
                    }
                    finally {
                        httpClient.disconnect()
                    }
                }
                else {
                    return "nothing"
                }
            } catch (e : UnknownHostException) {
                return "nothing"
            }
            return "nothing"
        }

        override fun onPostExecute(result: String?) {
            if (result == "nothing") {
                progressBar.visibility = View.INVISIBLE
                noInternet.visibility = View.VISIBLE
                googleMap?.clear()
                return
            }
            try {
                val jsonObject : JSONObject? = JSONObject(result!!)
                Log.d("call",result)
                val object2 = jsonObject?.opt("response") as JSONObject
                val venueArray = object2.optJSONArray("venues")
                if (venueArray?.length() != null) {
                    Log.d("call","C'è almeno uno")
                    (0 until venueArray.length()).forEach { i ->
                        val item = venueArray.optJSONObject(i)
                        val venueMain = VenueMain()
                        with(venueMain) {
                            id = item.optString("id")
                            name = item.optString("name")
                            locationJson = item.optString("location")
                            val categories = item.optJSONArray("categories")
                            if (categories != null && categories.length() > 0) {
                                categoriesJson = categories.optJSONObject(0).toString()
                            }
                        }
                        val location : JSONObject? = JSONObject(venueMain.locationJson!!)
                        val latitude = location?.optDouble("lat")
                        val longitude = location?.optDouble("lng")

                        val latLng = LatLng(latitude!!,longitude!!)
                        val boolean = PolyUtil.containsLocation(latLng,latLngValues,true)
                        if (boolean) {
                            val markerOptions : MarkerOptions = MarkerOptions()
                                .position(latLng)
                                .title(venueMain.name)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))

                            googleMap?.addMarker(markerOptions)

                            AllVenue.ITEMS.add(venueMain)
                            recyclerView.adapter?.notifyDataSetChanged()
                            progressBar.visibility = View.INVISIBLE
                        }
                        else {
                            progressBar.visibility = View.INVISIBLE
                        }
                    }
                }
                else {
                    progressBar.visibility = View.INVISIBLE
                    AllVenue.ITEMS.clear()
                    recyclerView.adapter?.notifyDataSetChanged()
                    Log.d("call","Nessun elemento trovato")
                }
                if (venueArray?.length() != null && AllVenue.ITEMS.size == 0) {
                    progressBar.visibility = View.INVISIBLE
                    Log.d("call","Nessun elemento trovato")
                    noElements.visibility = View.VISIBLE
                }
            } catch (e : JSONException) {
                Log.d("call",e.toString())
            }
            super.onPostExecute(result)
        }

    }

    companion object {
        const val VENUE_TO_ADD = 2010
    }

}
