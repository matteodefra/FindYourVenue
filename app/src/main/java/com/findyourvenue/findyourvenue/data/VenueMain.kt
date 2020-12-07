package com.findyourvenue.findyourvenue.data

/**
 * Oggetto AllVenue: utilizzo questo pattern singleton per creare una lista di VenueMain da tenere in memoria.
 * Sono tutte le località con le info ristrette che vengono mostrate nella Home al momento della ricerca, le tengo
 * in questa lista in modo da avere un accesso rapido al momento del caricamento di tutte le informazioni della località
 */
object AllVenue {

    val ITEMS : MutableList<VenueMain> = mutableListOf()

}

/**
 * Classe VenueMain: una sorta di "sottoclasse" di Venue, al momento della ricerca vengono memorizzate solo alcune
 * informazioni della località
 *
 * @param id id della località
 * @param name nome della località
 * @param locationJson stringa Json con latitude, longitudine e indirizzo della località
 * @param categoriesJson stringa Json per ricavare la categoria di cui fa parte la località
 */
class VenueMain(
    var id : String? = "",
    var name : String? = "",
    var locationJson : String? = "",
    var categoriesJson : String? = ""
)