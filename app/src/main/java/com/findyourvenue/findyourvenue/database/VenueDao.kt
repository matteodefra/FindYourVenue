package com.findyourvenue.findyourvenue.database

import android.database.Cursor
import androidx.room.*
import com.findyourvenue.findyourvenue.data.Venue

/**
 * Interfaccia VenueDao utilizzata dal contentProvider per chiamare i metodi
 */
@Dao
interface VenueDao {

    /**
     * Conta il numero di località nella tabella
     *
     * @return Il numero delle località.
     */
    @Query("SELECT COUNT(*) FROM ${Venue.TABLE_NAME}")
    fun count(): Int

    /**
     * Inserisce una località nella tabella
     *
     * @param venue Una nuova località.
     * @return L'ID della nuova località inserita
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(venue : Venue): Long

    /**
     * Inserisce piú località nel database
     *
     * @param venues Una lista di località.
     * @return Gli ID delle righe inserite.
     */
    @Insert
    fun insertAll(venues: MutableList<Venue>): LongArray?

    /**
     * Seleziona tutte le località.
     *
     * @return Un [Cursor] di tutte le località.
     */
    @Query("SELECT * FROM ${Venue.TABLE_NAME}")
    fun selectAll(): Cursor?

    /**
     * Seleziona una località in base all'ID.
     *
     * @param id L'ID della riga.
     * @return Un [Cursor] della località selezionata.
     */
    @Query("SELECT * FROM ${Venue.TABLE_NAME} WHERE ${Venue.COLUMN_ID} = :id")
    fun selectById(id: Long): Cursor?

    /**
     * Seleziona le località in base al nome
     *
     * @param name La stringa passata da cercare
     * @return Un [Cursor] delle località selezionate.
     */
    @Query("SELECT * FROM ${Venue.TABLE_NAME} WHERE ${Venue.COLUMN_NAME} LIKE :name")
    fun selectByName(name : String): Cursor?


    /**
     * Elimina una località in base all'ID
     *
     * @param id L'ID di riga.
     * @return Il numero di localita rimosse
     */
    @Query("DELETE FROM ${Venue.TABLE_NAME} WHERE ${Venue.COLUMN_ID} = :id")
    fun deleteById(id: Long): Int


    /**
     * Aggiorna una località in base all'ID, aggiunge foto ulteriori
     *
     * @param byteArray L'array di byte rappresentante la foto
     * @param id L'ID di riga.
     * @return Il numero di località aggiornate
     */
    @Query("UPDATE ${Venue.TABLE_NAME} SET ${Venue.COLUMN_SAVED_PICS} = :byteArray WHERE ${Venue.COLUMN_ID} = :id")
    fun updatePicture(byteArray: ByteArray,id : Long) : Int

}