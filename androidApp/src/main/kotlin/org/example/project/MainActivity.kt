package org.example.project

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import org.example.project.categories.presentation.ui.CategoriesRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                CategoriesRoute(
                    onOpenCategory = { categoryId -> Log.d("Categories", "Open $categoryId") }, // navigation comes later
                )
            }
        }
    }
}
