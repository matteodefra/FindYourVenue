package com.findyourvenue.findyourvenue

import android.Manifest
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.findyourvenue.findyourvenue.activity.StoreImage
import com.findyourvenue.findyourvenue.data.Venue
import com.findyourvenue.findyourvenue.fragments.FavouriteFragment
import com.findyourvenue.findyourvenue.fragments.FavouriteFragment.Companion.PICTURE_SAVED
import com.findyourvenue.findyourvenue.fragments.HomeFragment
import com.findyourvenue.findyourvenue.provider.MyContentProvider
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * Classe MainActivity: è l'activity di partenza, implementa il fusedLocationProvider per aggiornamenti sulla posizione
 * e gestisce tutta la richiesta di permessi all'utente
 */
class MainActivity : AppCompatActivity() {

    //FloatingActionButton per effettuare nuove foto
     private lateinit var fab: FloatingActionButton

    //FrameLayout per la gestione dei fragment
    private lateinit var frameLayout : FrameLayout

    //Booleano utilizzato per ripristinare lo stato dei fragment
    private var boolean = false

    private var actualLatitude: Double = 0.0
    private var actualLongitude: Double = 0.0

    //FuseLocationProvider per ottenere gli aggiornamenti sulla posizione
    private lateinit var fusedLocatioProviderClient : FusedLocationProviderClient
    
    /**
     * builder, client, task e locationRequest: vengono utilizzati per controllare se le impostazioni di localizzazione sono attivate
     * e per la risoluzione di problemi relativi a quest'ultima: da guida Google  https://developer.android.com/training/location/change-location-settings.html
     */
    private lateinit var builder : LocationSettingsRequest.Builder

    private lateinit var client : SettingsClient

    private lateinit var task : Task<LocationSettingsResponse>

    private var locationRequest: LocationRequest? = null

    private lateinit var currentPhotoPath: String


     @SuppressLint("ClickableViewAccessibility")
     override fun onCreate(savedInstanceState: Bundle?) {

         super.onCreate(savedInstanceState)
         setContentView(R.layout.activity_main)

         fusedLocatioProviderClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)

         fab = findViewById(R.id.fab)

         /**
          * Lancio un'Intent per acquisire una fotografia dalla camera (da https://developer.android.com/training/camera/photobasics.html)
          */
         fab.setOnClickListener {
             Log.d("call", "Inizio Intent per catturare la foto")
             Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                 // Ensure that there's a camera activity to handle the intent
                 takePictureIntent.resolveActivity(packageManager)?.also {
                     // Create the File where the photo should go
                     val photoFile: File? = try {
                         createImageFile()
                     } catch (ex: IOException) {
                         // Error occurred while creating the File
                         null
                     }
                     // Continue only if the File was successfully created
                     photoFile?.also {
                         val photoURI: Uri = FileProvider.getUriForFile(
                             this,
                             "com.example.android.fileprovider",
                             it
                         )
                         takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                         startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                     }
                 }
             }
         }

         frameLayout = findViewById(R.id.fragment_container)

         /**
          * Controllo se all'interno di onSavedInstanceState è presente il valore di Restore = true
          * Se cosi è, allora devo ripristinare l'applicazione dal fragment dei Preferiti, quindi eseguo
          * due commit di seguito per avviare l'HomeFragment e far partire il FavouriteFragment.
          * Altrimenti se non ho valori salvati nel bundle o non devo riaprire  FavouriteFragment
          * faccio partire HomeFragment
          */
         if (savedInstanceState != null) {
             Log.d("call","Ripristino lo stato dei Fragment")
             boolean = savedInstanceState.getBoolean("Restore")
             if (boolean) {
                 Log.d("call","Eseguo due commit per ritornare al Fragment Favourite")
                 supportFragmentManager.beginTransaction()
                     .replace(R.id.fragment_container,HomeFragment())
                     .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                     .commit()

                 supportFragmentManager.beginTransaction()
                     .replace(R.id.fragment_container,FavouriteFragment(),"Favourite")
                     .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                     .addToBackStack(null)
                     .commit()
             }
             else {
                 supportFragmentManager.beginTransaction()
                     .replace(R.id.fragment_container,HomeFragment())
                     .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                     .commit()
             }
         }
         else {
             supportFragmentManager.beginTransaction()
                 .replace(R.id.fragment_container,HomeFragment())
                 .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                 .commit()
         }


         /**
          * Qui effettuo il controllo dei permessi di localizzazione, in caso di permessi mancanti li richiedo,
          * altrimenti faccio partire un task che controlla se il GPS è attivo. Se non è attivo, non viene richiesto
          * sempre all'utente di attivarlo, in modo da risparmiare batteria! Se è gia presente una posizione salvata
          * nelle SharedPreferences allora evita di richiedere anche l'uso attivo del GPS.
          * In caso di richiesta di attivazione viene fatto partire una startResolutionForResult per richiedere l'attivazione
          */
         locationRequest = createLocationRequest()

         builder = LocationSettingsRequest.Builder()
             .addLocationRequest(createLocationRequest()!!)

         client = LocationServices.getSettingsClient(this)

         task =
             client.checkLocationSettings(builder.build())

         if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
             != PackageManager.PERMISSION_GRANTED
         ) {
             // Permission is not granted. Request for permission
             Log.d("call", "Richiedo i permessi di localizzazione a runtime")
             ActivityCompat.requestPermissions(
                 this,
                 arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                 MY_PERMISSIONS_ACCESS_LOCATION
             )
         } else {

             val lati = getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE).getString("Latitude","MISSING")!!
             val long = getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE).getString("Longitude","MISSING")!!
            if (lati == "MISSING" || long == "MISSING") {
                task.addOnSuccessListener {
                    // All location settings are satisfied. The client can initialize
                    // location requests here.
                    // ...
                    //MODIFICARE minTime and minDistance
                    Log.d("call", "Parte il fusedLocationProvider se è attivo il GPS. Parte ugualmente senza problemi anche perche" +
                            "il fused Location Provider combina piu strategie risolutive per ottenere la posizione")

                    fusedLocatioProviderClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        Looper.getMainLooper()
                    )

                }

                task.addOnFailureListener { exception ->
                    if (exception is ResolvableApiException) {

                        Log.d("call", "Qualcosa non va nelle impostazioni, chiedo all'utente di risolvere il problema " +
                                "con l'eccezione mostrata")
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            exception.startResolutionForResult(
                                this,
                                REQUEST_CHECK_SETTINGS
                            )
                        } catch (sendEx: IntentSender.SendIntentException) {
                            // Ignore the error.
                        }
                    }
                }
            }


         }

         /**
          * Utilizzo il Geocoder per ottenere l'indirizzo esatto in base a latitudine e longitudine,
          * per condividere via Intent.ACTION_SEND
          */
         if (Geocoder.isPresent()) {
             geocoder = Geocoder(this@MainActivity)
         }

         //Avvio un LoaderManager per caricare le località salvate nel database
         LoaderManager.getInstance(this).initLoader(1,null,mLoaderCallbacks)

     }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            locationResult ?: return
            val location = locationResult.locations[0]
            if (actualLatitude != 0.0 && actualLongitude != 0.0) {
                val previousLocation = Location("")
                previousLocation.latitude = actualLatitude
                previousLocation.longitude = actualLongitude
                if (location.distanceTo(previousLocation) > 100) {
                    actualLatitude = location.latitude
                    actualLongitude = location.longitude
                    val sharedPreferences : SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val editor : SharedPreferences.Editor = sharedPreferences.edit()
                    editor.putString("Latitude", actualLatitude.toString())
                    editor.putString("Longitude", actualLongitude.toString())
                    editor.apply()
                }
            }

        }
    }

    /**
     * La callBack del LoaderManager. Viene fatta partire qui in modo da non eseguire una richiesta al database
     * ogni volta che viene aperto il FavouriteFragment, tengo un oggetto condiviso di tipo cursor nel companion
     * object in modo che all'interno del FavouriteFragment possa accedere a questo cursor senza dover richiedere
     * anche li delle query al database
     */
     private val mLoaderCallbacks: LoaderManager.LoaderCallbacks<Cursor?> =
         object : LoaderManager.LoaderCallbacks<Cursor?> {
             override fun onCreateLoader(
                 id: Int,
                 args: Bundle?
             ): Loader<Cursor?> {
                 Log.d("call","Creo il loader")
                 return CursorLoader(
                     applicationContext,
                     MyContentProvider.URI_VENUE, null,
                     null, null, null
                 )
             }

             override fun onLoadFinished(
                 loader: Loader<Cursor?>,
                 data: Cursor?
             ) {
                 Log.d("call","Loader terminato")
                 cursor = data
                 listId.clear()
                 GlobalScope.launch(Dispatchers.Default) { saveData() }
             }

             override fun onLoaderReset(loader: Loader<Cursor?>) {
                 Log.d("call","Loader resettato")
                 cursor = null
             }
         }

    /**
     * Override del metodo onBackPressed, serve per gestire in caso in cui sono nel Fragment
     * dei preferiti, allora torno alla Home altrimenti chiudo l'applicazione
     */
     override fun onBackPressed() {
         if (supportFragmentManager.backStackEntryCount > 0) {
             Log.d("call","Caso Fragment Favourite: faccio il pop dello stack per tornare a Home")
             supportFragmentManager.popBackStackImmediate()
         }
         else {
             finish()
         }
     }

    /**
     * Override di onPause: fermo il fusedLocationProvider da aggiornamenti continui sulla posizione
     */
     override fun onPause() {
         super.onPause()
        fusedLocatioProviderClient.removeLocationUpdates(locationCallback)
     }

    /**
     * Anche nella onResume controllo che l'utente abbia i permessi necessari di localizzazione, in seguito controllo
     * se il task per controllare se il GPS è attivo è terminato, in tal caso avvio il fusedLocationProvider per ricevere aggiornamenti sulla posizione
     */
     override fun onResume() {
         super.onResume()
         if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
             != PackageManager.PERMISSION_GRANTED
         ) {
             // Permission is not granted. Request for permission
             Log.d("call", "Chiedo i permessi di localizzazione, protezione in piu")
             ActivityCompat.requestPermissions(
                 this,
                 arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                 MY_PERMISSIONS_ACCESS_LOCATION
             )

             // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
             // app-defined int constant. The callback method gets the
             // result of the request.
         }
         else {
             if (!task.isComplete) {
                 return
             }
             else {
                 Log.d("call", "Parte il fusedLocationProvider se il task è gia stato avviato nella onCreate")

                 fusedLocatioProviderClient.requestLocationUpdates(
                     locationRequest,
                     locationCallback,
                     Looper.getMainLooper()
                 )
             }

         }
     }

    /**
     * createLocationRequest: crea una semplice richiesta di posizione che viene utilizzata dal locationSettingsBuilder
     */
     private fun createLocationRequest(): LocationRequest? {
         return LocationRequest.create()?.apply {
             interval = 10000
             fastestInterval = 5000
             priority = LocationRequest.PRIORITY_HIGH_ACCURACY

         }
     }

     /**
      * Creazione del menu
      */
     override fun onCreateOptionsMenu(menu: Menu?): Boolean {
         menuInflater.inflate(R.menu.main_menu, menu)
         return true
     }

    /**
     * Gestione della selezione tra le entry del menu. Nel caso di choice_album, scelgo una fotografia
     * dalla galleria da aggiungere alle località salvate, nel caso invece di favourites, eseguo la commit
     * del FavouriteFragment se non è gia presente in foreground
     */
     override fun onOptionsItemSelected(item: MenuItem): Boolean {
         when (item.itemId) {
             R.id.choice_album -> {
                 Log.d("call", "Chiamo pickImage() per selezionare una foto dalla galleria")
                 pickImage()
                 return true
             }
             R.id.favourites -> {
                 Log.d("call","Faccio una commit di FavouriteFragment se non è gia presente")
                 val fragment : Fragment? = supportFragmentManager.findFragmentByTag("Favourite")
                 if (fragment != null && fragment.isVisible) {
                     return true
                 }
                 supportFragmentManager.beginTransaction()
                     .replace(R.id.fragment_container,FavouriteFragment(),"Favourite")
                     .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                     .addToBackStack(null)
                     .commit()
                 return true
             }
         }
         return super.onOptionsItemSelected(item)
     }


    /**
     * override di onSaveInstanceState: se il fragment corrente è FavouriteFragment salvo un valore booleano nel Bundle,
     * in modo che al ritorno nella OnCreate viene ripresa la giusta esecuzione
     */
     override fun onSaveInstanceState(outState: Bundle) {
         val fragment : Fragment? = supportFragmentManager.findFragmentByTag("Favourite")
         if (fragment != null && fragment.isVisible) {
             Log.d("call","Salvo lo stato del fragment: caso in cui l'utente ruota il telefono o mette l'app in background")
             outState.putBoolean("Restore",true)
         }
         super.onSaveInstanceState(outState)
     }

     /**
      * pickImage: metodo per selezionare una foto dalla galleria, vengono controllati i permessi di
      * READ_EXTERNAL_STORAGE, se si hanno viene lanciato un Intent.ACTION_PICK per scegliere la foto,
      * altrimenti si richiedono i permessi
      */
     private fun pickImage() {
         if (ActivityCompat.checkSelfPermission(
                 this,
                 READ_EXTERNAL_STORAGE
             ) == PackageManager.PERMISSION_GRANTED
         ) {
             val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
             startActivityForResult(intent, PICK_IMAGE_FROM_GALLERY)
         } else {
             ActivityCompat.requestPermissions(
                 this,
                 arrayOf(READ_EXTERNAL_STORAGE),
                 READ_EXTERNAL_STORAGE_REQUEST_CODE
             )
         }
     }

     /**
      * override di onRequestPermissionsResult: viene invocata per i permessi di READ_EXTERNAL_STORAGE,
      * se vengono accettati allora viene richiamata la funzione pickImage per scegliere la foto.
      * Per i permessi di localizzazione invece viene fatto partire nuovamente il task per controllare
      * se il GPS è attivo, nel caso non lo sia parte uno startResolutionForResult per chiederne l'attivazione,
      * altrimenti partono le richieste del locationManager con aggiornamenti sulla posizione
      *
      */
     @SuppressLint("MissingPermission")
     override fun onRequestPermissionsResult(
         requestCode: Int,
         permissions: Array<out String>,
         grantResults: IntArray
     ) {
         super.onRequestPermissionsResult(requestCode, permissions, grantResults)
         when (requestCode) {
             READ_EXTERNAL_STORAGE_REQUEST_CODE -> {
                 if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                     //Ora hai i permessi
                     pickImage()
                 }
             }
             MY_PERMISSIONS_ACCESS_LOCATION -> {
                 if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                     task.addOnSuccessListener {
                         // All location settings are satisfied. The client can initialize
                         // location requests here.
                         // ...
                         Log.d("call", "Parte il fusedLocationProvider")


                         fusedLocatioProviderClient.requestLocationUpdates(
                             locationRequest,
                             locationCallback,
                             Looper.getMainLooper()
                         )


                     }

                     task.addOnFailureListener { exception ->
                         if (exception is ResolvableApiException) {

                             // Location settings are not satisfied, but this can be fixed
                             // by showing the user a dialog.
                             try {
                                 // Show the dialog by calling startResolutionForResult(),
                                 // and check the result in onActivityResult().
                                 exception.startResolutionForResult(
                                     this,
                                     REQUEST_CHECK_SETTINGS
                                 )
                             } catch (sendEx: IntentSender.SendIntentException) {
                                 // Ignore the error.
                             }
                         }
                     }
                 }
             }
         }
     }

    /**
     * createImageFile: da android developer, crea un nome di un file da salvare nella directory esterna dell'applicazione
     *
     * @return Il nuovo file creato
     */
    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmm").format(Date())
        val storageDir : File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }


    /**
     * override di onActivityResult: viene invocata al ritorno di uno startActivityForResult:
     * Caso di REQUEST_IMAGE_CAPTURE, l'immagine catturata via fotocamera viene trasformata
     * in un byteArray per poi essere passata via Intent a StoreImage, per selezionare in quale località
     * memorizzare la foto.
     * Caso di PICK_IMAGE_FROM_GALLERY, l'immagine viene compressata con il metodo getResizedBitmap,
     * convertita in un byteArray dopodiche viene passsata via Intent a StoreImage.
     * Caso di REQUEST_CHECK_SETTINGS, caso in cui viene chiesto all'utente di attivare i servizi di
     * localizzazione, vengono controllati nuovamente i permessi di localizzazione e avvia il locationManager
     * Caso di VENUE_TO_ADD, quando l'utente aggiunge una località all'interno del database, allora viene riavviato
     * il LoaderManager per ricaricare il cursor nuovo modificato
     * Caso di PICTURE_SAVED: quando una nuova foto scattata o scelta dalla galleria viene salvata all'interno
     * di una località, allora viene riavviato il LoaderManager (se sono in HomeFragment) altrimenti se mi trovo
     * in FavouriteFragment delego a quest'ultimo la gestione di PICTURE_SAVED
     */
     override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
         when (requestCode) {
             REQUEST_IMAGE_CAPTURE -> {
                 if (resultCode == Activity.RESULT_OK) {

                     val intent = Intent(this@MainActivity, StoreImage::class.java)
                     intent.putExtra("path",currentPhotoPath)
                     intent.putExtra("case",1)
                     startActivityForResult(intent,PICTURE_SAVED)
                 }
             }
             PICK_IMAGE_FROM_GALLERY -> {
                 if (resultCode == Activity.RESULT_OK) {
                     val uri = data?.data
                     if (uri != null) {
                         try {
                             val imageUri: Uri? = data.data
                             val intent = Intent(this@MainActivity, StoreImage::class.java)
                             intent.putExtra("path",imageUri.toString())
                             intent.putExtra("case",2)
                             startActivityForResult(intent,PICTURE_SAVED)
                         } catch (e: FileNotFoundException) {
                             e.printStackTrace()
                             Toast.makeText(
                                 this@MainActivity,
                                 "Something went wrong",
                                 Toast.LENGTH_LONG
                             ).show()
                         }
                     }
                 }
             }
             REQUEST_CHECK_SETTINGS -> {
                 Log.d("call","Richiesta di attivazione del GPS")
                 if (ContextCompat.checkSelfPermission(
                         this,
                         Manifest.permission.ACCESS_FINE_LOCATION
                     )
                     != PackageManager.PERMISSION_GRANTED
                 ) {
                     // Permission is not granted. Request for permission
                     Log.d("call", "Controllo nuovamente i permessi di localizzazione")
                     ActivityCompat.requestPermissions(
                         this,
                         arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                         MY_PERMISSIONS_ACCESS_LOCATION
                     )

                     // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                     // app-defined int constant. The callback method gets the
                     // result of the request.
                 } else {
                     Log.d("call", "Parte il fusedLocationProvider")

                     fusedLocatioProviderClient.requestLocationUpdates(
                         locationRequest,
                         locationCallback,
                         Looper.getMainLooper()
                     )

                 }

             }
             HomeFragment.VENUE_TO_ADD -> {
                 if (resultCode == Activity.RESULT_OK) {
                     val type = data?.extras?.get("saved")
                     if (type == 1) {
                         LoaderManager.getInstance(this).restartLoader(1,null,mLoaderCallbacks)
                     }
                 }
             }
             PICTURE_SAVED -> {
                 val fragment : Fragment? = supportFragmentManager.findFragmentByTag("Favourite")
                 if (fragment != null && fragment.isVisible) {
                     fragment.onActivityResult(requestCode,resultCode, data)
                 }
                 else LoaderManager.getInstance(this).restartLoader(1,null,mLoaderCallbacks)
             }
             else -> super.onActivityResult(requestCode, resultCode, data)
         }
         super.onActivityResult(requestCode, resultCode, data)

     }

    /**
     * Utilizzo delle coroutines di Kotlin: salvo gli identificatori delle località salvate all'interno del database,
     * per l'aggiunta eventuale di foto in seguito. Task perfetto da eseguire con una coroutine poichè non contiene metodi bloccanti
     * si tratta semplicemente di una iterazione sul cursor
     */
    suspend fun saveData() = withContext(Dispatchers.Default) {
        if (cursor!= null && cursor?.moveToFirst()!!) {
            Log.d("call","Itero sul Cursor restituito dal LoaderManager")
            do {
                listId.add((cursor?.getLong(cursor?.getColumnIndexOrThrow(Venue.COLUMN_ID)!!)!!).toString())
            } while (cursor?.moveToNext()!!)
        }
    }

    /**
     * companion object della MainActivity, contiene il nome delle SharedPreferences, vari codici di ritorno
     * per onActivityResult e onRequestPermissionsResults, due liste nominate prima per i nomi e gli identificatori
     * e il cursor dei dati memorizzati per consentire l'accesso a FavouriteFragment
     */
     companion object  {
         const val PREFS_NAME = "MyPrefs"
         const val REQUEST_IMAGE_CAPTURE = 1
         const val PICK_IMAGE_FROM_GALLERY = 2

         const val READ_EXTERNAL_STORAGE_REQUEST_CODE = 1000

         const val MY_PERMISSIONS_ACCESS_LOCATION = 20001

         const val REQUEST_CHECK_SETTINGS = 2000

         var listId = ArrayList<String>()

         var cursor : Cursor? = null

        lateinit var geocoder : Geocoder
     }



 }

