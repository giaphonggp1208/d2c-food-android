package com.d2cfood.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.d2cfood.app.data.AuthRepository
import com.d2cfood.app.data.StoreRepository
import com.d2cfood.app.data.demo.DemoAuthRepository
import com.d2cfood.app.data.demo.DemoStoreRepository
import com.d2cfood.app.data.firebase.FirebaseAuthRepository
import com.d2cfood.app.data.firebase.FirestoreStoreRepository
import com.d2cfood.app.domain.Role
import com.d2cfood.app.domain.Store
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.launch

/**
 * Nhịp 1: role-based navigation + stable read-only Store/Menu screens + Email/Password login.
 *
 * Firebase Spark wiring: when a Firebase app is configured (google-services.json merged
 * by the plugin at build time) the real Email/Password Auth + Firestore repositories are
 * used; otherwise the in-memory demo repositories back the same screens so the UI stays
 * stable without a Firebase project (design D0: no billing, no Cloud Functions).
 *
 * Role model (design D3): `STORE_OWNER` is granted only by Admin seeding
 * `users/{uid}.roles`; the client can never self-elevate (enforced by
 * firestore.rules §users). Navigation picks the role from the profile.
 */
class MainActivity : ComponentActivity() {

    // Keep the Firebase SDK call inside a function body: firebase-common 21.0.0
    // exposes getApps() to Kotlin K2 as a method requiring an explicit Context.
    private val useFirebase: Boolean = hasFirebaseApp()

    private fun hasFirebaseApp(): Boolean = try {
        FirebaseApp.getApps(this).isNotEmpty()
    } catch (_: Throwable) {
        false
    }

    private val authRepository: AuthRepository =
        if (useFirebase) FirebaseAuthRepository() else DemoAuthRepository()
    private val storeRepository: StoreRepository =
        if (useFirebase) FirestoreStoreRepository() else DemoStoreRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            D2CFoodApp(authRepository, storeRepository)
        }
    }
}

@Composable
fun D2CFoodApp(authRepository: AuthRepository, storeRepository: StoreRepository) {
    // Plain local vals (not delegated state) so smart-cast works.
    val user = authRepository.currentUser().collectAsState(null).value
    val role = if (user != null && user.roles.contains(Role.STORE_OWNER)) Role.STORE_OWNER else Role.CUSTOMER
    val stores = storeRepository.stores().collectAsState(emptyList()).value
    var selectedStore by remember { mutableStateOf<Store?>(null) }
    val menu = storeRepository.menu(selectedStore?.id ?: "").collectAsState(emptyList()).value
    val scope = rememberCoroutineScope()

    MaterialTheme {
        if (user == null) {
            LoginScreen(authRepository)
        } else {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text("D2C Food", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text("Chào ${user.name.ifBlank { user.email }} — role: ${role.name}")
                TextButton(onClick = { scope.launch { authRepository.signOut() } }) {
                    Text("Đăng xuất")
                }
                Spacer(Modifier.height(12.dp))

                if (role == Role.CUSTOMER) {
                    Text("Chế độ Khách hàng: duyệt Store & Menu", style = MaterialTheme.typography.titleLarge)
                }
                if (role == Role.STORE_OWNER) {
                    // STORE_OWNER is Admin-seeded (design D3); the owner UI (menu editor,
                    // incoming orders, kitchen screen) lands in Nhịp 2–3. Nhịp 1 keeps the
                    // stable read path for both roles.
                    Text("Chế độ Chủ quán (cấp quyền bởi Admin): xem Store & Menu", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(8.dp))

                if (selectedStore == null) {
                    Text("Cửa hàng", style = MaterialTheme.typography.titleMedium)
                    LazyColumn {
                        items(stores) { store ->
                            ListItem(
                                headlineContent = { Text(store.name) },
                                supportingContent = { Text(store.address) },
                                trailingContent = { Button(onClick = { selectedStore = store }) { Text("Menu") } },
                            )
                        }
                    }
                } else {
                    val store = selectedStore!!
                    Text(store.name, style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { selectedStore = null }) { Text("← Cửa hàng") }
                    LazyColumn {
                        items(menu) { item ->
                            ListItem(
                                headlineContent = { Text(item.name) },
                                supportingContent = { Text(item.description) },
                                trailingContent = { Text("${item.basePrice}đ") },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Nhịp 1: Email/Password sign-in & register gate (Compose, no XML screens in this app). */
@Composable
fun LoginScreen(authRepository: AuthRepository) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("D2C Food", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Đăng nhập bằng Email/Password (Firebase Auth)\n" +
                "Đăng ký mới sẽ tạo hồ sơ CUSTOMER; STORE_OWNER do Admin cấp.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mật khẩu") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(16.dp))

        Button(
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            onClick = {
                busy = true
                error = null
                scope.launch {
                    val result = authRepository.signIn(email, password)
                    result.exceptionOrNull()?.let { failure ->
                        error = "Đăng nhập thất bại: ${failure.message}"
                    }
                    busy = false
                }
            },
        ) { Text("Đăng nhập") }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Tên (chỉ khi đăng ký)") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            onClick = {
                busy = true
                error = null
                scope.launch {
                    val result = authRepository.register(email, password, name)
                    result.exceptionOrNull()?.let { failure ->
                        error = "Đăng ký thất bại: ${failure.message}"
                    }
                    busy = false
                }
            },
        ) { Text("Đăng ký") }

        if (busy) {
            Spacer(Modifier.height(8.dp))
            Text("Đang xử lý…", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
