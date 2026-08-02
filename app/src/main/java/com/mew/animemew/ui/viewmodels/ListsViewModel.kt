package com.mew.animemew.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mew.animemew.data.local.AnimeDatabase
import com.mew.animemew.data.local.AnimeListEntity
import com.mew.animemew.data.local.AnimeListWithAnimes
import com.mew.animemew.data.local.LocalAnimeEntity
import com.mew.animemew.data.local.AnimeListCrossRef
import com.mew.animemew.data.sync.SyncManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ListsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AnimeDatabase.getDatabase(application).animeDao()
    private val syncManager = SyncManager.getInstance(application)

    val allListsWithAnimes: StateFlow<List<AnimeListWithAnimes>> = dao.getListsWithAnimes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createList(name: String) {
        viewModelScope.launch {
            dao.insertList(AnimeListEntity(name = name))
            syncManager.pushAsync()
        }
    }

    fun deleteList(animeList: AnimeListEntity) {
        if (animeList.isDefault) return
        viewModelScope.launch {
            dao.deleteList(animeList)
            // NUEVO: registrar borrado para evitar que el sync lo traiga de vuelta
            syncManager.registerListDeletion(animeList.id)
            syncManager.pushAsync()
        }
    }

    fun removeAnimeFromList(listId: Int, animeId: Int) {
        viewModelScope.launch {
            dao.removeAnimeFromListById(listId, animeId)
            syncManager.pushAsync()
        }
    }
}
