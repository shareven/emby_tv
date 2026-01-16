package com.xxxx.emby_tv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor
import com.xxxx.emby_tv.util.ErrorHandler
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class LocalServer private constructor(
    port: Int,
    private val mode: Mode,
    private val onLoginConfigReceived: ((String, String, String, String, String) -> Unit)? = null,
    private val onSearchReceived: ((String) -> Unit)? = null
) : NanoHTTPD(port) {

    enum class Mode {
        LOGIN, SEARCH
    }

    companion object {
        private var themePrimary: String = "#448AFF"
        private var themeSecondary: String = "#E040FB"

        fun startServer(
            themePrimaryDark: Color,
            themeSecondaryLight: Color,
            onConfigReceived: (String, String, String, String, String) -> Unit
        ): LocalServer? {
            updateThemeColors(themePrimaryDark, themeSecondaryLight)
            return startInternal(Mode.LOGIN, onLoginConfigReceived = onConfigReceived)
        }

        fun startSearchServer(
            themePrimaryDark: Color,
            themeSecondaryLight: Color,
            onSearchReceived: (String) -> Unit
        ): LocalServer? {
            updateThemeColors(themePrimaryDark, themeSecondaryLight)
            return startInternal(Mode.SEARCH, onSearchReceived = onSearchReceived)
        }

        private fun updateThemeColors(primary: Color, secondary: Color) {
            val primaryArgb = primary.toArgb()
            val primaryRgb = AndroidColor.rgb(
                AndroidColor.red(primaryArgb),
                AndroidColor.green(primaryArgb),
                AndroidColor.blue(primaryArgb)
            )
            val secondaryArgb = secondary.toArgb()
            val secondaryRgb = AndroidColor.rgb(
                AndroidColor.red(secondaryArgb),
                AndroidColor.green(secondaryArgb),
                AndroidColor.blue(secondaryArgb)
            )
            themePrimary = String.format("#%06X", 0xFFFFFF and primaryRgb)
            themeSecondary = String.format("#%06X", 0xFFFFFF and secondaryRgb)
        }

        private fun startInternal(
            mode: Mode,
            onLoginConfigReceived: ((String, String, String, String, String) -> Unit)? = null,
            onSearchReceived: ((String) -> Unit)? = null
        ): LocalServer? {
            var port = 4000
            while (port < 4010) {
                try {
                    val server = LocalServer(port, mode, onLoginConfigReceived, onSearchReceived)
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    return server
                } catch (e: IOException) {
                    port++
                }
            }
            return null
        }
    }

    override fun serve(session: IHTTPSession): Response {
        return when (mode) {
            Mode.LOGIN -> serveLogin(session)
            Mode.SEARCH -> serveSearch(session)
        }
    }

    private fun serveSearch(session: IHTTPSession): Response {
        if (session.method == Method.POST) {
            try {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val params = session.parms
                val query = params["query"] ?: ""

                if (query.isNotEmpty()) {
                    onSearchReceived?.invoke(query)
                    return serveSuccessPage("Search Sent!", "The search query has been sent to your TV.", "搜索已发送", "搜索词已发送到电视。")
                }
            } catch (e: Exception) {
                ErrorHandler.logError("LocalServer", "Error processing search", e)
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error parsing request")
            }
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Emby TV Search</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta charset="UTF-8">
                <style>
                    :root { --theme-primary: $themePrimary; --theme-secondary: $themeSecondary; }
                    body {
                        font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        padding: 0; margin: 0;
                        background: linear-gradient(135deg, var(--theme-primary), var(--theme-secondary));
                        color: white;
                        display: flex; justify-content: center; align-items: center;
                        min-height: 100vh;
                    }
                    .container {
                        background: rgba(0, 0, 0, 0.2);
                        padding: 40px;
                        border-radius: 16px;
                        backdrop-filter: blur(10px);
                        box-shadow: 0 4px 30px rgba(0, 0, 0, 0.3);
                        width: 90%; max-width: 400px;
                        border: 1px solid rgba(255, 255, 255, 0.1);
                    }
                    h2 { text-align: center; margin-bottom: 30px; font-weight: 300; }
                    input {
                        width: 100%; padding: 14px; margin-bottom: 20px;
                        box-sizing: border-box;
                        background: rgba(0, 0, 0, 0.3);
                        border: 1px solid rgba(255, 255, 255, 0.2);
                        border-radius: 8px; color: white; font-size: 16px;
                    }
                    input:focus { border-color: var(--theme-primary); outline: none; }
                    button {
                        width: 100%; padding: 14px;
                        background: white; color: black;
                        border: none; border-radius: 8px;
                        font-size: 16px; font-weight: bold; cursor: pointer;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2 id="title">Emby TV Search</h2>
                    <form method="POST">
                        <input type="text" name="query" id="input-query" placeholder="Enter keywords..." required autofocus>
                        <button type="submit" id="btn-submit">Search on TV</button>
                    </form>
                </div>
                <script>
                    if (navigator.language.startsWith('zh')) {
                        document.getElementById('title').innerText = 'Emby TV 搜索';
                        document.getElementById('input-query').placeholder = '输入搜索关键词...';
                        document.getElementById('btn-submit').innerText = '在电视上搜索';
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        return newFixedLengthResponse(html)
    }

    private fun serveLogin(session: IHTTPSession): Response {
        if (session.method == Method.POST) {
            try {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val params = session.parms

                val protocol = params["protocol"] ?: "http"
                val host = params["host"] ?: ""
                val port = params["port"] ?: "8096"
                val username = params["username"] ?: ""
                val password = params["password"] ?: ""

                if (host.isNotEmpty()) {
                    onLoginConfigReceived?.invoke(protocol, host, port, username, password)
                    return serveSuccessPage("Configuration Sent!", "You can verify the login on your TV.", "配置已发送！", "请在电视上验证登录。")
                }
            } catch (e: Exception) {
                ErrorHandler.logError("LocalServer", "服务器错误", e)
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error parsing request")
            }
        }
        
        // Serve the form
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Emby TV Login</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta charset="UTF-8">
                <style>
                    :root {
                        --theme-primary: $themePrimary;
                        --theme-secondary: $themeSecondary;
                    }
                    body {
                        font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        padding: 0;
                        margin: 0;
                        background: linear-gradient(135deg, var(--theme-primary), var(--theme-secondary));
                        color: white;
                        display: flex;
                        flex-wrap: wrap;
                        justify-content: center;
                        align-items: flex-start;
                        min-height: 100vh;
                    }
                    .container {
                        background: transparent;
                        padding: 40px 20px;
                        width: 90%;
                        max-width: 400px;
                        margin-top: 20px;
                        margin-bottom: 20px;
                    }
                    h2 {
                        text-align: center;
                        margin-bottom: 30px;
                        font-weight: 300;
                        letter-spacing: 1px;
                    }
                    label {
                        display: block;
                        margin-bottom: 8px;
                        font-size: 14px;
                        color: #ccc;
                    }
                    .input-row {
                        display: flex;
                        gap: 8px;
                        margin-bottom: 20px;
                        align-items: center;
                        justify-content: center;
                    }
                    .input-row input {
                        width: 100%;
                        padding: 12px;
                        box-sizing: border-box;
                        background: rgba(0, 0, 0, 0.3);
                        border: 1px solid rgba(255, 255, 255, 0.2);
                        border-radius: 8px;
                        color: white;
                        font-size: 16px;
                    }
                    .input-row input:focus {
                        border-color: #448AFF;
                        outline: none;
                    }
                    .protocol-btn {
                        padding: 12px 20px;
                        background: rgba(255, 255, 255, 0.1);
                        border: 1px solid rgba(255, 255, 255, 0.2);
                        border-radius: 8px;
                        color: white;
                        font-size: 16px;
                        cursor: pointer;
                        transition: all 0.2s;
                        min-width: 80px;
                        margin-bottom: 20px;
                    }
                    .protocol-btn.active {
                        background: rgba(255, 255, 255, 0.2);
                    }
                    .protocol { flex: 0 0 80px; }
                    .host { flex: 1; }
                    .port { flex: 0 0 80px; }
                    input {
                        width: 100%;
                        padding: 12px;
                        margin-bottom: 20px;
                        box-sizing: border-box;
                        background: rgba(0, 0, 0, 0.3);
                        border: 1px solid rgba(255, 255, 255, 0.2);
                        border-radius: 8px;
                        color: white;
                        font-size: 16px;
                        transition: border-color 0.3s;
                    }
                    input::placeholder {
                        color: rgba(255, 255, 255, 0.5);
                    }
                    input:focus {
                        border-color: var(--theme-primary);
                        outline: none;
                    }
                    button {
                        width: 100%;
                        padding: 14px;
                        background: white;
                        color: black;
                        border: none;
                        border-radius: 8px;
                        font-size: 16px;
                        font-weight: bold;
                        cursor: pointer;
                        transition: transform 0.2s, opacity 0.2s;
                    }
                    button:active {
                        transform: scale(0.98);
                        opacity: 0.9;
                    }
                    .success {
                        text-align: center;
                        color: #4CAF50;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2 id="form-title">Emby TV Login</h2>
                    <form method="POST" id="login-form">
                        <label id="label-url">Server URL</label>
                        <div class="input-row">
                            <input type="hidden" name="protocol" id="protocol-input" value="http">
                            <button type="button" class="protocol protocol-btn active" id="btn-protocol" onclick="toggleProtocol()">HTTP</button>
                            <input type="text" name="host" class="host" placeholder="192.168.1.x" required>
                            <input type="text" name="port" class="port" placeholder="Port" value="8096">
                        </div>
                        <label id="label-user">Username</label>
                        <input type="text" name="username" id="input-username" placeholder="Username" required>
                        <label id="label-pass">Password</label>
                        <input type="password" name="password" id="input-password" placeholder="Password" required>
                        <button type="submit" id="btn-submit">Send to TV</button>
                    </form>
                </div>
                <script>
                    function toggleProtocol() {
                        var btn = document.getElementById('btn-protocol');
                        var input = document.getElementById('protocol-input');
                        if (input.value === 'http') {
                            input.value = 'https';
                            btn.innerText = 'HTTPS';
                        } else {
                            input.value = 'http';
                            btn.innerText = 'HTTP';
                        }
                    }
                    if (navigator.language.startsWith('zh')) {
                        document.getElementById('form-title').innerText = 'Emby TV 登录';
                        document.getElementById('label-url').innerText = '服务器地址';
                        document.getElementById('label-user').innerText = '用户名';
                        document.getElementById('input-username').placeholder = '用户名';
                        document.getElementById('label-pass').innerText = '密码';
                        document.getElementById('input-password').placeholder = '密码';
                        document.getElementById('btn-submit').innerText = '发送到电视';
                    }
                    var lastScrollY = 0;
                    if (window.visualViewport) {
                        window.visualViewport.addEventListener('resize', function() {
                            var container = document.querySelector('.container');
                            if (window.visualViewport.height < window.innerHeight) {
                                var diff = window.innerHeight - window.visualViewport.height;
                                var containerBottom = container.getBoundingClientRect().bottom;
                                if (containerBottom > window.visualViewport.height - 50) {
                                    var scrollAmount = containerBottom - (window.visualViewport.height - 100);
                                    window.scrollTo({ top: scrollAmount, behavior: 'smooth' });
                                }
                            } else {
                                if (lastScrollY > 0) {
                                    window.scrollTo({ top: 0, behavior: 'smooth' });
                                }
                            }
                        });
                    }
                    var inputs = document.querySelectorAll('input');
                    inputs.forEach(function(input) {
                        input.addEventListener('focus', function() {
                            setTimeout(function() {
                                var btn = document.getElementById('btn-submit');
                                btn.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            }, 300);
                        });
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
        
        return newFixedLengthResponse(html)
    }

    private fun serveSuccessPage(enTitle: String, enDesc: String, zhTitle: String, zhDesc: String): Response {
        val successHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Success</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta charset="UTF-8">
                <style>
                    :root { --theme-primary: $themePrimary; --theme-secondary: $themeSecondary; }
                    body {
                        font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        padding: 0; margin: 0;
                        background: linear-gradient(135deg, var(--theme-primary), var(--theme-secondary));
                        color: white;
                        display: flex; justify-content: center; align-items: center;
                        min-height: 100vh;
                    }
                    .container {
                        background: rgba(255, 255, 255, 0.1);
                        padding: 40px; border-radius: 16px;
                        backdrop-filter: blur(10px);
                        box-shadow: 0 4px 30px rgba(0, 0, 0, 0.5);
                        width: 90%; max-width: 400px; text-align: center;
                    }
                    h2 { margin-bottom: 20px; font-weight: 300; }
                    p { color: #ccc; margin-bottom: 30px; }
                    .icon { font-size: 64px; margin-bottom: 20px; color: var(--theme-primary); }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="icon">✓</div>
                    <h2 id="success-title">$enTitle</h2>
                    <p id="success-desc">$enDesc</p>
                </div>
                <script>
                    if (navigator.language.startsWith('zh')) {
                        document.getElementById('success-title').innerText = '$zhTitle';
                        document.getElementById('success-desc').innerText = '$zhDesc';
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        return newFixedLengthResponse(successHtml)
    }
}
