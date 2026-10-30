package com.chatku.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chatku.app.ServiceLocator
import com.chatku.app.data.Session

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val repo = ServiceLocator.repo
    val token by Session.token.collectAsState()
    val pending by repo.pendingOpenChat.collectAsState()

    LaunchedEffect(token) {
        val route = nav.currentDestination?.route
        if (token != null) {
            repo.start()
            if (route == "auth") nav.navigate("chats") { popUpTo("auth") { inclusive = true } }
        } else {
            repo.stop()
            if (route != "auth") nav.navigate("auth") { popUpTo(nav.graph.id) { inclusive = true } }
        }
    }

    LaunchedEffect(pending, token) {
        val id = pending
        if (id != null && token != null) {
            nav.navigate("chat/$id")
            repo.pendingOpenChat.value = null
        }
    }

    NavHost(nav, startDestination = if (Session.token.value == null) "auth" else "chats") {
        composable("auth") { AuthScreen() }
        composable("chats") {
            ChatListScreen(
                onOpenChat = { nav.navigate("chat/$it") },
                onNewChat = { nav.navigate("newchat") },
                onNewGroup = { nav.navigate("newgroup") },
                onProfile = { nav.navigate("profile") },
            )
        }
        composable("chat/{chatId}", arguments = listOf(navArgument("chatId") { type = NavType.StringType })) { e ->
            val id = e.arguments?.getString("chatId").orEmpty()
            ChatScreen(id, onBack = { nav.popBackStack() }, onGroupInfo = { nav.navigate("groupinfo/$id") })
        }
        composable("newchat") {
            NewChatScreen(
                onBack = { nav.popBackStack() },
                onOpenChat = { id -> nav.navigate("chat/$id") { popUpTo("newchat") { inclusive = true } } },
                onNewGroup = { nav.navigate("newgroup") },
            )
        }
        composable("newgroup") {
            NewGroupScreen(
                onBack = { nav.popBackStack() },
                onCreated = { id -> nav.navigate("chat/$id") { popUpTo("chats") } },
            )
        }
        composable("groupinfo/{chatId}", arguments = listOf(navArgument("chatId") { type = NavType.StringType })) { e ->
            val id = e.arguments?.getString("chatId").orEmpty()
            GroupInfoScreen(id, onBack = { nav.popBackStack() }, onLeft = { nav.popBackStack("chats", false) })
        }
        composable("profile") { ProfileScreen(onBack = { nav.popBackStack() }) }
    }
}
