package com.bioluck.copywork

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.bioluck.copywork.ui.ArticleDetailScreen
import com.bioluck.copywork.ui.HomeScreen
import com.bioluck.copywork.ui.theme.CopyworkTheme
import com.bioluck.copywork.update.InAppUpdateHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CopyworkTheme { Box { CopyworkNav(); InAppUpdateHost() } } }
    }
}

@Composable
private fun CopyworkNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(onArticle = { nav.navigate("article/$it") }) }
        composable("article/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            ArticleDetailScreen(articleId = it.arguments!!.getLong("id"), onBack = nav::popBackStack)
        }
    }
}
