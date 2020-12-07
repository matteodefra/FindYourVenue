package com.findyourvenue.findyourvenue.activity

import android.content.Context
import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cursoradapter.widget.CursorAdapter
import com.findyourvenue.findyourvenue.data.Venue

/**
 * Class MyCursorAdapter per popolare la lista di StoreImage con i dati del Cursor
 *
 * @param context : contesto dell'activity StoreImage
 * @param c: cursor per il recupero dei dati
 */
class MyCursorAdapter(context: Context?, c: Cursor?) : CursorAdapter(context, c,true) {

    private var inflater: LayoutInflater = LayoutInflater.from(context)


    override fun bindView(view: View, context: Context?, cursor: Cursor) {
        val textView : TextView = view.findViewById(android.R.id.text1)
        textView.text = cursor.getString(cursor.getColumnIndexOrThrow(Venue.COLUMN_NAME))
    }

    override fun newView(context: Context?, cursor: Cursor?, parent: ViewGroup?): View {
        return inflater.inflate(android.R.layout.simple_list_item_single_choice, parent, false)
    }

}