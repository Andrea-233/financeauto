package com.financeauto

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeauto.data.local.AppDatabase
import com.financeauto.data.remote.SupabaseManager
import com.financeauto.data.remote.UpdateManager
import com.financeauto.data.repository.CardRepository
import com.financeauto.data.repository.TransactionRepository
import com.financeauto.ui.cards.CardsScreen
import com.financeauto.ui.cards.CardsViewModel
import com.financeauto.ui.screen.*
import com.financeauto.viewmodel.TransactionViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(this)
        val transactionRepo = TransactionRepository(database.transactionDao())
        val cardRepo = CardRepository(database.cardDao())
        val transactionVM = TransactionViewModel(transactionRepo)
        val cardsVM = CardsViewModel(cardRepo)
        val supabaseManager = SupabaseManager(this)

        setContent {
            MaterialTheme {
                var isLoggedIn by remember { mutableStateOf(supabaseManager.isLoggedIn) }
                var showProfile by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                if (!isLoggedIn) {
                    AuthScreen(onLoginSuccess = {
                        isLoggedIn = true
                        // Check for update and fetch subscription after login
                        scope.launch {
                            supabaseManager.fetchSubscription()
                            val updateManager = UpdateManager(context)
                            updateManager.checkForUpdate().fold(
                                onSuccess = { info ->
                                    if (info != null) {
                                        Toast.makeText(context, "发现新版本 ${info.versionName}，可在个人中心更新", Toast.LENGTH_LONG).show()
                                    }
                                },
                                onFailure = { /* silent */ }
                            )
                        }
                    })
                } else {
                    val profile = remember { supabaseManager.getProfile() }
                    var nickname by remember { mutableStateOf(profile.nickname) }
                    var expiryDate by remember { mutableStateOf(profile.expiryDate) }
                    var planType by remember { mutableStateOf(profile.planType) }
                    var selectedTab by remember { mutableStateOf(0) }
                    var readerOpen by remember { mutableStateOf(false) }
                    var showAdmin by remember { mutableStateOf(false) }

                    if (showAdmin) {
                        AdminScreen(
                            supabaseManager = supabaseManager,
                            onBack = {
                                showAdmin = false
                                // Refresh cached subscription after admin changes
                                expiryDate = supabaseManager.currentExpiryDate
                                planType = supabaseManager.currentPlanType
                            }
                        )
                    } else if (showProfile) {
                        ProfileScreen(
                            profile = profile.copy(nickname = nickname, expiryDate = expiryDate, planType = planType),
                            supabaseManager = supabaseManager,
                            onLogout = {
                                showProfile = false
                                isLoggedIn = false
                            },
                            onProfileUpdated = {
                                nickname = supabaseManager.currentNickname
                                expiryDate = supabaseManager.currentExpiryDate
                                planType = supabaseManager.currentPlanType
                            },
                            onDismiss = { showProfile = false },
                            onAdminPanel = { showAdmin = true }
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(Color(0xFFFFF8E7))) {
                            when (selectedTab) {
                                0 -> HomeScreen(
                                    vm = transactionVM,
                                    nickname = nickname,
                                    onProfileClick = { showProfile = true },
                                    expiryDate = expiryDate,
                                    planType = planType
                                )
                                1 -> ChatPage(
                                    records = transactionVM.transactions.collectAsState(initial = emptyList()).value,
                                    hasBalanceAnchor = false,
                                    balanceAnchorDate = 0L,
                                    balanceAnchorAmount = 0.0
                                )
                                2 -> RecordsScreen(vm = transactionVM)
                                3 -> CardsScreen(vm = cardsVM)
                                4 -> CalendarScreen(vm = transactionVM)
                                5 -> BooksScreen(onReaderState = { readerOpen = it })
                            }

                            if (!readerOpen) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color.White,
                                    shadowElevation = 8.dp
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        NavTab("首页", "H", selectedTab == 0) { selectedTab = 0 }
                                        NavTab("AI", "C", selectedTab == 1) { selectedTab = 1 }
                                        NavTab("记录", "R", selectedTab == 2) { selectedTab = 2 }
                                        NavTab("卡片", "D", selectedTab == 3) { selectedTab = 3 }
                                        NavTab("日历", "M", selectedTab == 4) { selectedTab = 4 }
                                        NavTab("图书", "B", selectedTab == 5) { selectedTab = 5 }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavTab(label: String, icon: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor = if (selected) Color(0xFF2979FF) else Color.Transparent
    val contentColor = if (selected) Color.White else Color(0xFF74768A)
    val weight = if (selected) FontWeight.Bold else FontWeight.Normal
    Surface(
        modifier = Modifier.padding(horizontal = 1.dp),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 15.sp)
            Text(label, fontSize = 10.sp, fontWeight = weight, color = contentColor)
        }
    }
}
