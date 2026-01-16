package com.xxxx.emby_tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.session.AccountInfo
import com.xxxx.emby_tv.ui.components.BuildItem
import com.xxxx.emby_tv.ui.components.Loading
import com.xxxx.emby_tv.ui.components.NoData
import com.xxxx.emby_tv.ui.components.TvInputDialog
import com.xxxx.emby_tv.ui.viewmodel.LoginViewModel
import com.xxxx.emby_tv.ui.viewmodel.SearchViewModel
import com.xxxx.emby_tv.util.ErrorHandler
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshotFlow

/**
 * 搜索界面
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel,
    loginViewModel: LoginViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSeries: (String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { EmbyRepository.getInstance(context) }
    val serverUrl = repository.serverUrl ?: ""

    // 状态
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchResults = searchViewModel.searchResults
    val isLoading = searchViewModel.isLoading
    val isLoadingMore = searchViewModel.isLoadingMore
    val hasMoreData = searchViewModel.hasMoreData
    val totalCount = searchViewModel.totalCount
    val currentQuery = searchViewModel.currentQuery

    // 账号列表
    val savedAccounts = loginViewModel.savedAccounts
    val currentAccountId = loginViewModel.currentAccountId

    // 焦点管理
    val firstItemFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    // 搜索错误处理
    LaunchedEffect(searchViewModel.errorMessage) {
        searchViewModel.errorMessage?.let { error ->
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.search_failed, error),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            // 清除错误消息
            searchViewModel.errorMessage = null
        }
    }

    // 监听滚动位置，接近底部时加载更多
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

    // 数据加载完成后聚焦到第一个项目
    LaunchedEffect(searchResults) {
        if (searchResults != null && searchResults.isNotEmpty()) {
            kotlinx.coroutines.delay(100)
            firstItemFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 账号选择器（横向）
        if (savedAccounts.isNotEmpty()) {
            AccountSelectorRow(
                accounts = savedAccounts,
                currentAccountId = currentAccountId,
                onAccountFocused = { accountId ->
                    if (accountId != currentAccountId) {
                        // 切换账号（直接使用保存的apikey，不需要重新认证）
                        loginViewModel.switchAccount(
                            accountId,
                            onSuccess = {
                                // 切换成功，重新搜索
                                if (currentQuery.isNotEmpty()) {
                                    searchViewModel.refreshSearch()
                                }
                            },
                            onError = { error ->
                                // 切换失败，显示toast提示
                                android.widget.Toast.makeText(
                                    context,
                                    "切换账号失败: $error",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 搜索输入区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 搜索按钮
            Surface(
                onClick = { showSearchDialog = true },
                modifier = Modifier
                    .height(48.dp)
                    .width(200.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))),
                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                ),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color.White.copy(alpha = 0.7f),
                    contentColor = Color.White,
                    focusedContentColor = Color.Black
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (currentQuery.isNotEmpty()) currentQuery else stringResource(R.string.search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 显示搜索结果数量
            if (searchResults != null && totalCount > 0) {
                Text(
                    text = "${searchResults.size}/$totalCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        // 搜索结果展示
        if (searchResults == null && isLoading) {
            Loading()
        } else if (searchResults != null) {
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_search_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            } else {
                val items = searchResults

                // 计算宽高
                val maxLength = 220.dp
                val maxAspectRatio = items.mapNotNull {
                    val ratio = it.primaryImageAspectRatio?.toFloat()
                    if (ratio == null || ratio == 1.0f) null else ratio
                }.maxOrNull() ?: 0.666f

                val imgWidth = if (maxAspectRatio >= 1f) {
                    maxLength
                } else {
                    (maxLength.value * maxAspectRatio).dp
                }
                
                val num = if (maxAspectRatio > 1) 4 else 6
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(num),
                    state = gridState,
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 数据项
                    items(items.size, key = { items[it].id ?: it.hashCode() }) { index ->
                        val item = items[index]
                        val id = item.id ?: ""
                        if (id.isNotEmpty()) {
                            val itemModifier = if (index == 0) {
                                Modifier.fillMaxWidth().focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier.fillMaxWidth()
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                BuildItem(
                                    item = item,
                                    imgWidth = imgWidth,
                                    aspectRatio = maxAspectRatio,
                                    modifier = itemModifier,
                                    isMyLibrary = false,
                                    serverUrl = serverUrl,
                                    onItemClick = {
                                        onNavigateToSeries(id)
                                    }
                                )
                            }
                        }
                    }
                    
                    // 加载更多指示器
                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 搜索输入对话框
    if (showSearchDialog) {
        TvInputDialog(
            title = stringResource(R.string.search),
            initialValue = currentQuery,
            onConfirm = { query ->
                searchQuery = query
                showSearchDialog = false
                searchViewModel.search(query)
            },
            onDismiss = { showSearchDialog = false }
        )
    }
}

/**
 * 账号选择器（横向）
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AccountSelectorRow(
    accounts: List<AccountInfo>,
    currentAccountId: String?,
    onAccountFocused: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
    ) {
        itemsIndexed(
            accounts,
            key = { _, account -> account.id }
        ) { index, account ->
            val isCurrentAccount = account.id == currentAccountId
            val focusRequester = remember { FocusRequester() }

            // 监听焦点变化
            LaunchedEffect(Unit) {
                if (index == 0) {
                    kotlinx.coroutines.delay(100)
                    focusRequester.requestFocus()
                }
            }

            Surface(
                onClick = { onAccountFocused(account.id) },
                modifier = Modifier
                    .width(280.dp)
                    .height(64.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            onAccountFocused(account.id)
                        }
                    },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                border = ClickableSurfaceDefaults.border(
                    border = if (isCurrentAccount) {
                        Border(BorderStroke(2.dp, Color(0xFF4CAF50)))
                    } else {
                        Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)))
                    },
                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                ),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isCurrentAccount) {
                        Color(0xFF4CAF50).copy(alpha = 0.12f)
                    } else {
                        Color.White.copy(alpha = 0.05f)
                    },
                    focusedContainerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White,
                    focusedContentColor = Color.White
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 用户图标
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isCurrentAccount) Color(0xFF4CAF50).copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.1f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isCurrentAccount) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // 账号信息
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = account.displayName.ifEmpty { account.username },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = account.serverUrl,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.55f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 当前账号标记
                    if (isCurrentAccount) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }
    }
}
