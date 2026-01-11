package com.xxxx.emby_tv

import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class LocalServer(port: Int, private val onConfigReceived: (String, String, String) -> Unit) : NanoHTTPD(port) {

    companion object {
        fun startServer(onConfigReceived: (String, String, String) -> Unit): LocalServer? {
            var port = 4000
            while (port < 4010) {
                try {
                    val server = LocalServer(port, onConfigReceived)
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
        if (session.method == Method.POST) {
            try {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val params = session.parms
                
                val url = params["url"] ?: ""
                val username = params["username"] ?: ""
                val password = params["password"] ?: ""
                
                if (url.isNotEmpty()) {
                    onConfigReceived(url, username, password)
                    val successHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <title>Emby TV Login</title>
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <meta charset="UTF-8">
                            <style>
                                body {
                                    font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                                    padding: 0;
                                    margin: 0;
                                    background: linear-gradient(135deg, #122240 0%, #2e0e36 100%);
                                    color: white;
                                    display: flex;
                                    justify-content: center;
                                    align-items: center;
                                    min-height: 100vh;
                                }
                                .container {
                                    background: rgba(255, 255, 255, 0.1);
                                    padding: 40px;
                                    border-radius: 16px;
                                    backdrop-filter: blur(10px);
                                    box-shadow: 0 4px 30px rgba(0, 0, 0, 0.5);
                                    width: 90%;
                                    max-width: 400px;
                                    text-align: center;
                                }
                                h2 { margin-bottom: 20px; font-weight: 300; }
                                p { color: #ccc; margin-bottom: 30px; }
                                .icon { font-size: 64px; margin-bottom: 20px; color: #4CAF50; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="icon">✓</div>
                                <h2 id="success-title">Configuration Sent!</h2>
                                <p id="success-desc">You can verify the login on your TV.</p>
                            </div>
                            <script>
                                if (navigator.language.startsWith('zh')) {
                                    document.getElementById('success-title').innerText = '配置已发送！';
                                    document.getElementById('success-desc').innerText = '请在电视上验证登录。';
                                }
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    return newFixedLengthResponse(successHtml)
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
                    body {
                        font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        padding: 0;
                        margin: 0;
                        background: linear-gradient(135deg, #122240 0%, #2e0e36 100%);
                        color: white;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                    }
                    .container {
                        background: rgba(255, 255, 255, 0.1);
                        padding: 40px;
                        border-radius: 16px;
                        backdrop-filter: blur(10px);
                        box-shadow: 0 4px 30px rgba(0, 0, 0, 0.5);
                        width: 90%;
                        max-width: 400px;
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
                    input:focus {
                        border-color: #448AFF;
                        outline: none;
                    }
                    button {
                        width: 100%;
                        padding: 14px;
                        background: linear-gradient(90deg, #448AFF, #E040FB);
                        color: white;
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
                    <form method="POST">
                        <label id="label-url">Server URL</label>
                        <input type="text" name="url" placeholder="http://192.168.1.x:8096" required value="http://">
                        <label id="label-user">Username</label>
                        <input type="text" name="username" id="input-username" placeholder="Username">
                        <label id="label-pass">Password</label>
                        <input type="password" name="password" id="input-password" placeholder="Password">
                        <button type="submit" id="btn-submit">Send to TV</button>
                    </form>
                </div>
                <script>
                    if (navigator.language.startsWith('zh')) {
                        document.getElementById('form-title').innerText = 'Emby TV 登录';
                        document.getElementById('label-url').innerText = '服务器地址';
                        document.getElementById('label-user').innerText = '用户名';
                        document.getElementById('input-username').placeholder = '用户名';
                        document.getElementById('label-pass').innerText = '密码';
                        document.getElementById('input-password').placeholder = '密码';
                        document.getElementById('btn-submit').innerText = '发送到电视';
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        
        return newFixedLengthResponse(html)
    }
}
