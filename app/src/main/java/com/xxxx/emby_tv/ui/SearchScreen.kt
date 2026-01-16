package com.xxxx.emby_tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.xxxx.emby_tv.LocalServer
import com.xxxx.emby_tv.QrCodeUtils
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.R.*
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.session.AccountInfo
import com.xxxx.emby_tv.ui.components.BuildItem
import com.xxxx.emby_tv.ui.components.Loading
import com.xxxx.emby_tv.ui.components.TvInputDialog
import com.xxxx.emby_tv.ui.theme.ThemeColorManager
import com.xxxx.emby_tv.ui.viewmodel.LoginViewModel
import com.xxxx.emby_tv.ui.viewmodel.MainViewModel
import com.xxxx.emby_tv.ui.viewmodel.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Search Screen with QR Code input and redesigned layout.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel,
    loginViewModel: LoginViewModel,
    mainViewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSeries: (String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { EmbyRepository.getInstance(context) }
    val serverUrl = repository.serverUrl ?: ""
    val themeColor = ThemeColorManager.getThemeColorById(context, mainViewModel.currentThemeId)

    // State
    var showSearchDialog by remember { mutableStateOf(false) }
    val searchResults = searchViewModel.searchResults
    val isLoading = searchViewModel.isLoading
    val isLoadingMore = searchViewModel.isLoadingMore
    val hasMoreData = searchViewModel.hasMoreData
    val totalCount = searchViewModel.totalCount
    val currentQuery = searchViewModel.currentQuery
    
    // Server & QR Code State
    var qrCodeBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var localServerAddress by remember { mutableStateOf("") }
    var localServer by remember { mutableStateOf<LocalServer?>(null) }

    // Account List
    val savedAccounts = loginViewModel.savedAccounts
    val currentAccountId = loginViewModel.currentAccountId

    // Focus
    val searchInputFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    // Start Local Server for Search
    LaunchedEffect(themeColor) {
        withContext(Dispatchers.IO) {
            val server = LocalServer.startSearchServer(themeColor.primaryDark, themeColor.secondaryLight) { query ->
                // Switch to main thread to update query
                launch(Dispatchers.Main) {
                    searchViewModel.search(query)
                }
            }
            localServer = server
            if (server != null) {
                val ip = QrCodeUtils.getLocalIpAddress()
                if (ip != null) {
                    val address = "http://$ip:${server.listeningPort}"
                    localServerAddress = address
                    val bitmap = QrCodeUtils.generateQrCode(address, 200) // Smaller QR code
                    qrCodeBitmap = bitmap?.asImageBitmap()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            localServer?.stop()
        }
    }

    // Error Handling
    LaunchedEffect(searchViewModel.errorMessage) {
        searchViewModel.errorMessage?.let { error ->
            android.widget.Toast.makeText(
                context,
                context.getString(string.search_failed, error),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            searchViewModel.errorMessage = null
        }
    }

    // Scroll to load more
    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            
            totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 5
        }.collectLatest { shouldLoadMore ->
            if (shouldLoadMore && hasMoreData && !isLoadingMore) {
                searchViewModel.loadMore()
            }
        }
    }

    // Initial Focus
    LaunchedEffect(Unit) {
        delay(100)
        searchInputFocusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // Left Sidebar: Account List
        if (savedAccounts.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .padding(end = 24.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = stringResource(string.switch_account),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                    )
                }
                
                itemsIndexed(savedAccounts, key = { _, acc -> acc.id }) { _, account ->
                    val isSelected = account.id == currentAccountId
                    val domain = try {
                         val uri = java.net.URI(account.serverUrl)
                         uri.host ?: account.serverUrl
                    } catch (e: Exception) {
                        account.serverUrl
                    }
                    val displayText = "${account.username}@$domain"

                    Surface(
                        onClick = { 
                             if (!isSelected) {
                                 loginViewModel.switchAccount(
                                     account.id,
                                     onSuccess = {
                                         if (currentQuery.isNotEmpty()) {
                                             searchViewModel.refreshSearch()
                                         }
                                     },
                                     onError = {
                                         android.widget.Toast.makeText(context, "Switch failed", android.widget.Toast.LENGTH_SHORT).show()
                                     }
                                 )
                             }
                        },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                            focusedContainerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White,
                            focusedContentColor = Color.White
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = if (isSelected) Border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))) else Border(BorderStroke(0.dp, Color.Transparent)),
                            focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary))
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
                            )

                        }
                    }
                }
            }
            
            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.1f))
            )
            
            Spacer(modifier = Modifier.width(24.dp))
        }

        // Right Content: Search Input + Results
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Header: Search Input + QR Code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Input Button
                Surface(
                    onClick = { showSearchDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .focusRequester(searchInputFocusRequester),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))),
                        focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary))
                    ),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (currentQuery.isNotEmpty()) currentQuery else stringResource(
                                string.search_placeholder),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = if (currentQuery.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.5f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))

                // QR Code (Small)
                if (qrCodeBitmap != null) {
                    Surface(
                        onClick = { /* No-op */ },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White,
                            focusedContainerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
                            Row( horizontalArrangement = Arrangement.Center){

                                 Image(
                                    bitmap = qrCodeBitmap!!,
                                    contentDescription = "Scan to search",
                                    modifier = Modifier.fillMaxSize()
                                )
                                Text(
                                    text = stringResource(string.scan_qr_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color =Color.White
                                )
                            }
                        }
                    }
                }
            }
            
            // Search Results Info
            if (searchResults != null && totalCount > 0) {
                Text(
                    text = "${searchResults.size} / $totalCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Search Results Grid
            if (searchResults == null && isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Loading()
                }
            } else if (searchResults != null) {
                if (searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(string.no_search_results),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    val items = searchResults
                     val maxLength = 200.dp
                     val maxAspectRatio = items.mapNotNull {
                        val ratio = it.primaryImageAspectRatio?.toFloat()
                        if (ratio == null || ratio == 1.0f) null else ratio
                    }.maxOrNull() ?: 0.666f

                    val imgWidth = if (maxAspectRatio >= 1f) maxLength else (maxLength.value * maxAspectRatio).dp
                    val numStart = if (maxAspectRatio > 1) 4 else 5

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(numStart),
                        state = gridState,
                        contentPadding = PaddingValues(top=10.dp,bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items.size, key = { items[it].id ?: it.hashCode() }) { index ->
                            val item = items[index]
                            val id = item.id ?: ""
                            if (id.isNotEmpty()) {
                                 BuildItem(
                                    item = item,
                                    imgWidth = imgWidth,
                                    aspectRatio = maxAspectRatio,
                                    modifier = Modifier.fillMaxWidth(),
                                    isMyLibrary = false,
                                    serverUrl = serverUrl,
                                    onItemClick = { onNavigateToSeries(id) }
                                )
                            }
                        }
                        if (isLoadingMore) {
                            item {
                                Box(modifier = Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                                    androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Input Dialog
    if (showSearchDialog) {
        TvInputDialog(
            title = stringResource(string.search),
            initialValue = currentQuery,
            onConfirm = { query ->
                showSearchDialog = false
                searchViewModel.search(query)
            },
            onDismiss = { showSearchDialog = false }
        )
    }
}
