package com.lantianhcgp.erbian.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lantianhcgp.erbian.ui.channel.ChannelPage
import com.lantianhcgp.erbian.ui.profile.ProfilePage
import com.lantianhcgp.erbian.ui.schedule.SchedulePage
import com.lantianhcgp.erbian.ui.settings.SettingsPage
import com.lantianhcgp.erbian.ui.theme.ErbianShapes
import com.lantianhcgp.erbian.ui.theme.ErbianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            ErbianTheme(
                dynamicColor = vm.config.dynamicColor,
                pureBlack = vm.config.pureBlack,
            ) {
                HomeScreen(vm)
            }
        }
    }
}

private data class Tab(val label: String, val on: ImageVector, val off: ImageVector)

private val tabs = listOf(
    Tab("频道", Icons.Filled.Chat, Icons.Outlined.Chat),
    Tab("日程", Icons.Filled.Event, Icons.Outlined.Event),
    Tab("画像", Icons.Filled.Person, Icons.Outlined.Person),
    Tab("设置", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@androidx.compose.runtime.Composable
fun HomeScreen(vm: AppViewModel) {
    var page by remember { mutableIntStateOf(0) }

    androidx.compose.material3.MaterialTheme {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn(tween220()) togetherWith fadeOut(tween220()) },
                    modifier = Modifier.weight(1f),
                ) { p ->
                    when (p) {
                        0 -> ChannelPage(vm)
                        1 -> SchedulePage(vm)
                        2 -> ProfilePage(vm)
                        else -> SettingsPage(vm)
                    }
                }
            }
            Surface(
                shape = ErbianShapes.Pill,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    tabs.forEachIndexed { i, tab ->
                        val selected = page == i
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .semantics { contentDescription = i.toString() }
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                                .clickable { page = i },
                        ) {
                            Icon(
                                if (selected) tab.on else tab.off,
                                contentDescription = tab.label,
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun tween220() = androidx.compose.animation.core.tween<Int>(220)
