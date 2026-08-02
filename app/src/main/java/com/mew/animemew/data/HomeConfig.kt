package com.mew.animemew.data

import com.google.gson.annotations.SerializedName

// =========================================================
//  Configuración remota del Home.
//  Se descarga desde el server y define qué secciones mostrar.
// =========================================================

data class HomeConfig(
    @SerializedName("sections")
    val sections: List<HomeSectionConfig> = emptyList()
)

data class HomeSectionConfig(
    @SerializedName("id")
    val id: String,                    // identificador único: "popular", "action", etc.
    @SerializedName("title")
    val title: String,                 // "Lo Más Popular", "Acción Nivel Dios"
    @SerializedName("type")
    val type: String,                  // "popular", "trending", "genre"
    @SerializedName("genre")
    val genre: String? = null,         // "Action", "Romance" (solo si type == "genre")
    @SerializedName("sort")
    val sort: String? = null           // "FAVOURITES_DESC", "SCORE_DESC" (solo si type == "genre")
)
