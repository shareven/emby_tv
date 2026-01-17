package com.xxxx.emby_tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.data.session.AccountInfo
import com.xxxx.emby_tv.ui.viewmodel.MainViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AccountScreen(
    mainViewModel: MainViewModel,
    savedAccounts: List<AccountInfo>,
    currentAccountId: String?,
    onBack: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onAddAccount: () -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    // 删除确认对话框状态
    var showDeleteDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<AccountInfo?>(null) }

    // 删除确认对话框
    if (showDeleteDialog && accountToDelete != null) {
        DeleteConfirmDialog(
            accountInfo = accountToDelete!!,
            onConfirm = {
                onDeleteAccount(accountToDelete!!.id)
                showDeleteDialog = false
                accountToDelete = null
            },
            onDismiss = {
                showDeleteDialog = false
                accountToDelete = null
            }
        )
    }

    // 居中布局
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 主卡片容器 - 使用深色半透明背景与渐变背景形成对比
        Surface(
            modifier = Modifier
                .width(700.dp)
                .heightIn(min = 400.dp, max = 550.dp),
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(
                containerColor = Color.Transparent
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                // 标题
                Text(
                    text = stringResource(R.string.account_management),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 账号列表区域
                if (savedAccounts.isEmpty()) {
                    // 无保存账号提示
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_saved_accounts),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 18.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            savedAccounts,
                            key = { _, account -> account.id }
                        ) { index, account ->
                            val isCurrentAccount = account.id == currentAccountId
                            AccountListItem(
                                account = account,
                                isCurrentAccount = isCurrentAccount,
                                isFirst = index == 0,
                                firstItemFocusRequester = firstItemFocusRequester,
                                onSelect = {
                                    if (!isCurrentAccount) {
                                        onSwitchAccount(account.id)
                                    }
                                },
                                onDelete = {
                                    accountToDelete = account
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 底部按钮区
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 添加账号按钮
                    Surface(
                        onClick = onAddAccount,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .then(
                                if (savedAccounts.isEmpty()) Modifier.focusRequester(firstItemFocusRequester)
                                else Modifier
                            ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White.copy(alpha = 0.95f),
                            contentColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.add_account),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    // 返回按钮
                    Surface(
                        onClick = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White.copy(alpha = 0.95f),
                            contentColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.back),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // 自动聚焦
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        firstItemFocusRequester.requestFocus()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AccountListItem(
    account: AccountInfo,
    isCurrentAccount: Boolean,
    isFirst: Boolean,
    firstItemFocusRequester: FocusRequester,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    // 账号项和删除按钮分开，使用 Row 布局
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 账号信息卡片（可点击切换）
        Surface(
            onClick = onSelect,
            modifier = Modifier
                .weight(1f)
                .height(72.dp)
                .then(if (isFirst) Modifier.focusRequester(firstItemFocusRequester) else Modifier),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            border = ClickableSurfaceDefaults.border(
                border = if (isCurrentAccount) {
                    Border(BorderStroke(2.dp,  MaterialTheme.colorScheme.tertiary))
                } else {
                    Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)))
                },
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isCurrentAccount)  MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.2f)
                else  Color.White.copy(alpha = 0.05f)
                ,
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                contentColor = if (isCurrentAccount)  MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary,
                focusedContentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 用户图标
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                             Color.White.copy(alpha = 0.1f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // 账号信息
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = account.displayName.ifEmpty { account.username },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = account.serverUrl,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 当前账号标记
                if (isCurrentAccount) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // 删除按钮（独立可聚焦）
        Surface(
            onClick = onDelete,
            modifier = Modifier.size(72.dp),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))),
                focusedBorder = Border(BorderStroke(2.dp, Color(0xFFEF5350)))
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.05f),
                focusedContainerColor = Color(0xFFEF5350),
                contentColor = Color.White.copy(alpha = 0.6f),
                focusedContentColor = Color.White
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete_account),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DeleteConfirmDialog(
    accountInfo: AccountInfo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cancelFocusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(420.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                colors = SurfaceDefaults.colors(
                    containerColor = Color(0xFF1C1C1C),
                    contentColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 标题
                    Text(
                        text = stringResource(R.string.delete_account),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 确认信息
                    Text(
                        text = stringResource(R.string.confirm_delete_account),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 账号名称
                    Text(
                        text = accountInfo.displayName.ifEmpty { accountInfo.username },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFEF5350)
                        )
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // 按钮组
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 取消按钮
                        Surface(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .focusRequester(cancelFocusRequester),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                focusedContainerColor = Color.White.copy(alpha = 0.9f),
                                contentColor = Color.White,
                                focusedContentColor = Color.Black
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        // 确认按钮
                        Surface(
                            onClick = onConfirm,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color(0xFFEF5350).copy(alpha = 0.7f),
                                focusedContainerColor = Color(0xFFEF5350),
                                contentColor = Color.White,
                                focusedContentColor = Color.White
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.confirm),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        cancelFocusRequester.requestFocus()
    }
}
