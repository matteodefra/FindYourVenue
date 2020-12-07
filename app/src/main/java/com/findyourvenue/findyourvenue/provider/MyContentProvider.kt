package com.findyourvenue.findyourvenue.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.findyourvenue.findyourvenue.data.Venue
import com.findyourvenue.findyourvenue.database.MyDatabase
import com.findyourvenue.findyourvenue.database.VenueDao

/**
 * Classe MyContentProvider: eredita da ContentProvider
 */
class MyContentProvider : ContentProvider() {

    /**
     * MATCHER: oggetto UriMatcher per rilevare la richiesta, 1 su tutta la tabella, 2 per un singolo elemento
     */
    private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {

        addURI(AUTHORITY,Venue.TABLE_NAME,1)

        addURI(AUTHORITY,"${Venue.TABLE_NAME}/#",2)
    }

    override fun onCreate(): Boolean {
        return true
    }

    /**
     * Override query: viene recuperato l'istanza di venueDao dal database, dopodiché se il MATCHER trova 1,
     * se tra i selectionArgs è presente una stringa, allora sto chiedendo la ricerca per nome, nel caso della
     * barra di ricerca dei preferiti, altrimenti richiedo tutte le località salvata
     * Nel caso 2 invece sto chiedendo una singola località contenuta nella URI passata come parametro.
     * Alla fine restituisco il Cursor per iterare attraverso le righe
     */
    override fun query(
        uri: Uri, projection: Array<String?>?, selection: String?,
        selectionArgs: Array<String?>?, sortOrder: String?
    ): Cursor? {
        val code: Int = MATCHER.match(uri)
        if (context == null) return null
        val venueDao : VenueDao = MyDatabase.getInstance(context!!)!!.venueDao()
        val cursor : Cursor?
        cursor = when (code) {
            1 -> {
                if (selection != null) {
                    val args = selectionArgs?.get(0)
                    venueDao.selectByName(args!!)
                }
                else {
                    venueDao.selectAll()
                }
            }
            2 -> {
                venueDao.selectById(ContentUris.parseId(uri))
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
        cursor?.setNotificationUri(context!!.contentResolver,uri)
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return when (MATCHER.match(uri)) {
            1 -> "vnd.android.cursor.dir/$AUTHORITY.${Venue.TABLE_NAME}"
            2 -> "vnd.android.cursor.item/$AUTHORITY.${Venue.TABLE_NAME}"
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    /**
     * Override di insert, viene utilizzato il matcher nel caso 1, i dati da inserire vengono mandati attraverso
     * values, un contentValues che viene recuperato tramite la funzione fromContentValues della classe Venue
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return when (MATCHER.match(uri)) {
            1 -> {
                Log.d("call","Sono qui, sta inserendo")
                val context = context ?: return null
                val id: Long = MyDatabase.getInstance(context)?.venueDao()
                    ?.insert(Venue.fromContentValues(values))
                    ?: return null
                context.contentResolver.notifyChange(uri, null )
                ContentUris.withAppendedId(uri, id)
            }
            2-> throw IllegalArgumentException("Invalid URI, cannot insert with ID: $uri")
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    /**
     * Override di delete, nel caso 2 il Matcher trova la URI corrispondente con all'interno l'ID di riga
     * dell'elemento da rimuovere
     */
    override fun delete(
        uri: Uri, selection: String?,
        selectionArgs: Array<String?>?
    ): Int {
        return when (MATCHER.match(uri)) {
            1 -> throw IllegalArgumentException("Invalid URI, cannot update without ID$uri")
            2 -> {
                Log.d("call","entra qui?")
                val context = context ?: return 0
                val count: Int = MyDatabase.getInstance(context)!!.venueDao()
                    .deleteById(ContentUris.parseId(uri))
                context.contentResolver.notifyChange(uri, null )
                count
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    /**
     * Override di update: utilizzata nel caso 2, viene passata una URI con l'ID di riga corrispondente
     * e nei ContentValues un'immagine da salvare a quella riga indicata dall'ID.
     */
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<String?>?
    ): Int {
        return when (MATCHER.match(uri)) {
            1 -> throw IllegalArgumentException("Invalid URI, cannot update without ID$uri")
            2 -> {
                val context = context ?: return 0
                val venue: Venue = Venue.fromContentValues(values)
                venue.ID = ContentUris.parseId(uri)
                val count: Int = MyDatabase.getInstance(context)!!.venueDao()
                    .updatePicture(venue.imageBytes!!,venue.ID)
                context.contentResolver.notifyChange(uri, null)
                count
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    /**
     * Companion object con l'AUTHORITY e URI generale
     */
    companion object {

        const val AUTHORITY = "com.findyourvenue.findyourvenue.provider.MyContentProvider"
        val URI_VENUE = Uri.parse("content://$AUTHORITY/${Venue.TABLE_NAME}")
    }


}