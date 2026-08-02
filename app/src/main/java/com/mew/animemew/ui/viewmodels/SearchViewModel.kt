package com.mew.animemew.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mew.animemew.data.Anime
import com.mew.animemew.data.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val repository = AnimeRepository()

    private val _searchResults = MutableStateFlow<List<Anime>>(emptyList())
    val searchResults: StateFlow<List<Anime>> = _searchResults.asStateFlow()

    private val _genres = MutableStateFlow<List<String>>(emptyList())
    val genres: StateFlow<List<String>> = _genres.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentPage = 1
    private var hasMore = true
    private val perPage = 20
    
    private var currentQuery: String = ""
    private var currentSelectedGenres: List<String> = emptyList()

    init {
        loadGenres()
        search("", emptyList()) // Load initial discovery (trending)
    }

    private fun loadGenres() {
        viewModelScope.launch {
            try {
                val fetchedGenres = repository.getGenres()
                if (fetchedGenres != null) {
                    _genres.value = fetchedGenres
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun search(query: String, selectedGenres: List<String>) {
        currentQuery = query
        currentSelectedGenres = selectedGenres
        currentPage = 1
        hasMore = true
        _searchResults.value = emptyList()
        loadMore()
    }

    fun loadMore() {
        if (!hasMore || _isLoading.value) return
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // AniList API breaks if we pass empty string as search query, so we pass null instead
                val searchQuery = if (currentQuery.isBlank()) null else currentQuery
                val genresQuery = if (currentSelectedGenres.isEmpty()) null else currentSelectedGenres
                
                val page = repository.searchAnime(currentPage, perPage, searchQuery, genresQuery)
                if (page != null) {
                    val newAnimes = page.media?.filterNotNull()?.map {
                        Anime(
                            id = it.id.toString(),
                            title = it.title?.romaji ?: it.title?.english ?: "Unknown",
                            coverUrl = it.coverImage?.large ?: "",
                            score = (it.averageScore ?: 0) / 10.0,
                            type = it.format?.name ?: "Unknown"
                        )
                    } ?: emptyList()
                    
                    _searchResults.value = _searchResults.value + newAnimes
                    hasMore = page.pageInfo?.hasNextPage ?: false
                    currentPage++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
