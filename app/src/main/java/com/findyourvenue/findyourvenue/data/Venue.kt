package com.findyourvenue.findyourvenue.data

import android.content.ContentValues
import android.os.Parcel
import android.os.Parcelable
import android.provider.BaseColumns
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Classe Venue: l'oggetto località che viene salvato nel database e mostrato sulla UI all'utente
 *
 * @param ID identificatore per il database
 * @param venueId la stringa che identifica questa località
 * @param name il nome della località
 * @param location una stringa Json che contiene le informazioni sulla posizione,indirizzo ecc..
 * @param rating un valore numerico che rappresenta la votazione della località
 * @param photos una stringa Json contenente le URLs alle foto della località
 * @param description una stringa contenente la descrizione della località
 * @param tips una stringa Json contenente alcuni suggerimenti sulla località
 * @param listed una stringa Json contenente i riferimenti ai luoghi circostanti
 * @param hours una stringa Json contenente gli orari di apertura segnalati dalle persone
 * @param defaultHours una stringa Json contenente gli orari di default di apertura della località
 * @param attributes una stringa Json contenente alcune info su servizi presenti in zona
 */
@Entity(tableName = "Venue")
data class Venue constructor(
    @PrimaryKey(autoGenerate = true)@ColumnInfo(name = COLUMN_ID,index = true) var ID: Long = 0,
    @ColumnInfo(name = "venueId") var venueId: String? = "",
    @ColumnInfo(name = COLUMN_NAME) var name: String? = "",
    @ColumnInfo(name = "locationJson") var location: String? = "",
    @ColumnInfo(name = "rating") var rating: Double? = 0.0,
    @ColumnInfo(name = "photosJson") var photos: String? = "",
    @ColumnInfo(name = "description") var description: String? = "",
    @ColumnInfo(name = "tipsJson") var tips: String? = "",
    @ColumnInfo(name = "listedJson") var listed: String? = "",
    @ColumnInfo(name = "hoursJson") var hours: String? = "",
    @ColumnInfo(name = "defaultHoursJson") var defaultHours: String? = "",
    @ColumnInfo(name = "attributesJson") var attributes: String? = "",
    @ColumnInfo(name = "bestPhotoJson") var bestPhoto: String? = "",
    @ColumnInfo(name = "addedImages",typeAffinity = ColumnInfo.BLOB) var imageBytes : ByteArray? = byteArrayOf(0x00.toByte())
) : Parcelable {

    /**
     * Equivalente della funzione readFromParcel, utilizzata per leggere il Parcelable passato tramite Intent
     */
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readDouble(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString()
    ) {
        imageBytes = ByteArray(parcel.readInt())
        parcel.readByteArray(imageBytes!!)
    }

    /**
     * Funzione utilizzata per salvare l'oggetto Venue all'interno di un Parcel
     */
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(ID)
        parcel.writeString(venueId)
        parcel.writeString(name)
        parcel.writeString(location)
        if (rating == null) parcel.writeDouble(0.0)
        else parcel.writeDouble(this.rating!!)
        parcel.writeString(photos)
        parcel.writeString(description)
        parcel.writeString(tips)
        parcel.writeString(listed)
        parcel.writeString(hours)
        parcel.writeString(defaultHours)
        parcel.writeString(attributes)
        parcel.writeString(bestPhoto)
        parcel.writeInt(imageBytes?.size!!)
        parcel.writeByteArray(imageBytes)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Venue> {
        const val TABLE_NAME = "Venue"

        const val COLUMN_ID = BaseColumns._ID
        const val COLUMN_VENUE_ID = "venueId"
        const val COLUMN_NAME = "name"
        const val COLUMN_LOCATION = "locationJson"
        const val COLUMN_RATING = "rating"
        const val COLUMN_PHOTOS = "photosJson"
        const val COLUMN_DESCRIPTION = "description"
        const val COLUMN_TIPS = "tipsJson"
        const val COLUMN_LISTED = "listedJson"
        const val COLUMN_HOURS = "hoursJson"
        const val COLUMN_DEFAULT_HOURS = "defaultHoursJson"
        const val COLUMN_ATTRIBUTES = "attributesJson"
        const val COLUMN_BEST_PHOTO = "bestPhotoJson"
        const val COLUMN_SAVED_PICS = "addedImages"

        /**
         * Funzione fromContentValues utilizzata dal contentProvider per recuperare i valori dell'oggetto per poi salvare/aggiornare
         */
        fun fromContentValues(values: ContentValues?): Venue {
            val venue = Venue()
            if (values != null && values.containsKey(COLUMN_ID)) {
                venue.ID =
                    values.getAsLong(COLUMN_ID)
            }
            if (values != null && values.containsKey(COLUMN_VENUE_ID)) {
                venue.venueId =
                    values.getAsString(COLUMN_VENUE_ID)
            }
            if (values != null && values.containsKey(COLUMN_NAME)) {
                venue.name =
                    values.getAsString(COLUMN_NAME)
            }
            if (values != null && values.containsKey(COLUMN_LOCATION)) {
                venue.location =
                    values.getAsString(COLUMN_LOCATION)
            }
            if (values != null && values.containsKey(COLUMN_RATING)) {
                venue.rating =
                    values.getAsDouble(COLUMN_RATING)
            }
            if (values != null && values.containsKey(COLUMN_PHOTOS)) {
                venue.photos =
                    values.getAsString(COLUMN_PHOTOS)
            }
            if (values != null && values.containsKey(COLUMN_DESCRIPTION)) {
                venue.description =
                    values.getAsString(COLUMN_DESCRIPTION)
            }
            if (values != null && values.containsKey(COLUMN_TIPS)) {
                venue.tips =
                    values.getAsString(COLUMN_TIPS)
            }
            if (values != null && values.containsKey(COLUMN_LISTED)) {
                venue.listed =
                    values.getAsString(COLUMN_LISTED)
            }
            if (values != null && values.containsKey(COLUMN_HOURS)) {
                venue.hours =
                    values.getAsString(COLUMN_HOURS)
            }
            if (values != null && values.containsKey(COLUMN_DEFAULT_HOURS)) {
                venue.defaultHours =
                    values.getAsString(COLUMN_DEFAULT_HOURS)
            }
            if (values != null && values.containsKey(COLUMN_ATTRIBUTES)) {
                venue.attributes =
                    values.getAsString(COLUMN_ATTRIBUTES)
            }
            if (values != null && values.containsKey(COLUMN_BEST_PHOTO)) {
                venue.bestPhoto =
                    values.getAsString(COLUMN_BEST_PHOTO)
            }
            if (values != null && values.containsKey(COLUMN_SAVED_PICS)) {
                venue.imageBytes =
                    values.getAsByteArray(COLUMN_SAVED_PICS)
            }
            return venue
        }

        override fun createFromParcel(parcel: Parcel): Venue {
            return Venue(parcel)
        }

        override fun newArray(size: Int): Array<Venue?> {
            return arrayOfNulls(size)
        }
    }


}