import 'package:emby_tv/app_model.dart';
import 'package:emby_tv/pages/update_screen.dart';
import 'package:emby_tv/utils.dart';
import 'package:emby_tv/widgets/build_gradient_background.dart';
import 'package:emby_tv/widgets/tv_input_dialog.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:emby_tv/l10n/app_localizations.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _serverController = TextEditingController(
    text: '',
  );
  final TextEditingController _userController = TextEditingController(text: '');
  final TextEditingController _passController = TextEditingController(text: '');

  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _loadSavedCredentials();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadVersion();
    });
  }

  Future<void> _loadVersion() async {
    if (mounted) {
      await context.read<AppModel>().checkUpdate();
    }
  }

  Future<void> _loadSavedCredentials() async {
    final prefs = await SharedPreferences.getInstance();
    final savedServerUrl = prefs.getString('serverUrl') ?? '';
    final savedUsername = prefs.getString('username') ?? '';
    final savedPassword = prefs.getString('password') ?? '';
    _serverController.text = savedServerUrl;
    _userController.text = savedUsername;
    _passController.text = savedPassword;
  }

  @override
  Widget build(BuildContext context) {
    final model = context.watch<AppModel>();
    String versionUpdateNote = '';
    if (model.needUpdate) {
      versionUpdateNote = AppLocalizations.of(
        context,
      )!.newVersionAvailable(model.newVersion);
    }
    return Scaffold(
      body: BuildGradientBackground(
        child: Center(
          child: SizedBox(
            width: 500,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _buildTextField(
                  AppLocalizations.of(context)!.serverUrl,
                  _serverController,
                  autofocus: true,
                ),
                const SizedBox(height: 24),
                _buildTextField(
                  AppLocalizations.of(context)!.username,
                  _userController,
                ),
                const SizedBox(height: 24),
                _buildTextField(
                  AppLocalizations.of(context)!.password,
                  _passController,
                  obscureText: true,
                ),
                const SizedBox(height: 40),
                _buildLoginButton(),
                const SizedBox(height: 20),
                Padding(
                  padding: const EdgeInsets.only(bottom: 20),
                  child: Text(
                    versionUpdateNote.isNotEmpty
                        ? "${AppLocalizations.of(context)!.pressMenuToShowMenu}  ${model.currentVersion}  $versionUpdateNote"
                        : "",
                    style: const TextStyle(
                      color: Colors.pink,
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 24.0),
                  child: Column(
                    children: [
                      Container(
                        height: 1,
                        width: double.infinity,
                        margin: const EdgeInsets.only(bottom: 12),
                        color: Colors.white10,
                      ),
                      Center(
                        child: Container(
                          constraints: const BoxConstraints(maxWidth: 460),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 12,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.black.withValues(alpha: 0.28),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            crossAxisAlignment: CrossAxisAlignment.center,
                            children: [
                              Icon(
                                Icons.info_outline,
                                color: Theme.of(context).colorScheme.primary,
                                size: 18,
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(
                                  AppLocalizations.of(context)!.footerNotice,
                                  textAlign: TextAlign.left,
                                  softWrap: true,
                                  style: const TextStyle(
                                    color: Colors.white70,
                                    fontSize: 13,
                                    height: 1.25,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _showInputDialog(
    TextEditingController controller,
    String label, {
    bool isPassword = false,
  }) async {
    await showDialog(
      context: context,
      builder: (context) => TvInputDialog(
        controller: controller,
        label: label,
        isPassword: isPassword,
      ),
    );
  }

  Widget _buildTextField(
    String label,
    TextEditingController controller, {
    bool obscureText = false,
    autofocus = false,
  }) {
    Color primaryColor = Theme.of(context).colorScheme.primary;
    bool needUpdate = context.watch<AppModel>().needUpdate;
    return Focus(
      autofocus: autofocus,
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent) {
          final key = event.logicalKey;
          if (key == LogicalKeyboardKey.accept ||
              key == LogicalKeyboardKey.select ||
              key == LogicalKeyboardKey.enter ||
              key == LogicalKeyboardKey.arrowRight) {
            _showInputDialog(
              controller,
              label,
              isPassword: label == AppLocalizations.of(context)!.password,
            );
            return KeyEventResult.handled;
          }

          if (key == LogicalKeyboardKey.contextMenu && needUpdate) {
            _showMenuDialog();
            return KeyEventResult.handled;
          }
        }
        return KeyEventResult.ignored;
      },
      child: Builder(
        builder: (context) {
          FocusNode node = Focus.of(context);
          bool isFocused = node.hasFocus;
          return AnimatedContainer(
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeInOut,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(12),
              boxShadow: [
                if (isFocused)
                  BoxShadow(
                    color: primaryColor.withValues(alpha: 0.3),
                    blurRadius: 20,
                    spreadRadius: 2,
                  ),
              ],
            ),
            child: TextField(
              controller: controller,
              obscureText: obscureText,
              enabled: false,
              style: const TextStyle(fontSize: 20, color: Colors.white),
              decoration: InputDecoration(
                labelText: label,
                labelStyle: TextStyle(
                  fontSize: 18,
                  color: isFocused ? primaryColor : Colors.white70,
                ),
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 18,
                ),
                filled: true,
                fillColor: Colors.black.withValues(alpha: 0.3),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide(color: primaryColor, width: 2),
                ),
                suffixIcon: isFocused
                    ? Icon(Icons.arrow_forward, color: primaryColor)
                    : null,
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildLoginButton() {
    Color primaryColor = Theme.of(context).colorScheme.primary;
    bool needUpdate = context.watch<AppModel>().needUpdate;
    return Focus(
      autofocus: false,
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent) {
          final key = event.logicalKey;
          if (key == LogicalKeyboardKey.accept ||
              key == LogicalKeyboardKey.select ||
              key == LogicalKeyboardKey.enter) {
            _handleLogin();
            return KeyEventResult.handled;
          }
          if (key == LogicalKeyboardKey.contextMenu && needUpdate) {
            _showMenuDialog();
            return KeyEventResult.handled;
          }
        }
        return KeyEventResult.ignored;
      },
      child: Builder(
        builder: (context) {
          FocusNode node = Focus.of(context);
          bool isFocused = node.hasFocus;
          return AnimatedContainer(
            duration: const Duration(milliseconds: 100),
            curve: Curves.easeInOut,
            transform: Matrix4.diagonal3Values(
              isFocused ? 1.05 : 1.0,
              isFocused ? 1.05 : 1.0,
              1.0,
            ),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(12),
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: isFocused
                    ? [
                        Colors.blueAccent.withValues(alpha: 0.3 + 0.2 * 0.6),
                        Colors.purpleAccent.withValues(
                          alpha: 0.2 + 0.2 * (1 - 0.6),
                        ),
                      ]
                    : [Colors.grey[500]!, Colors.grey[600]!],
              ),
              boxShadow: [
                if (isFocused)
                  BoxShadow(
                    color: primaryColor.withValues(alpha: 0.4),
                    blurRadius: 20,
                    spreadRadius: 2,
                  ),
              ],
            ),
            child: ElevatedButton(
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                  vertical: 18,
                  horizontal: 48,
                ),
                backgroundColor: Colors.transparent,
                shadowColor: Colors.transparent,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              onPressed: _handleLogin,
              child: Text(
                _isLoading
                    ? AppLocalizations.of(context)!.loggingIn
                    : AppLocalizations.of(context)!.login,
                style: TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  @override
  void dispose() {
    _serverController.dispose();
    _userController.dispose();
    _passController.dispose();

    super.dispose();
  }

  void _showMenuDialog() async {
    await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: Colors.black.withValues(alpha: 0.6),
        content: SizedBox(
          width: 800,
          height: 600,
          child: ListView(
            children: [
              Focus(
                autofocus: true,
                onKeyEvent: (node, event) {
                  if (event is KeyDownEvent) {
                    final key = event.logicalKey;
                    if (key == LogicalKeyboardKey.accept ||
                        key == LogicalKeyboardKey.select ||
                        key == LogicalKeyboardKey.enter) {
                      Navigator.pop(context);
                      return KeyEventResult.handled;
                    }
                  }
                  return KeyEventResult.ignored;
                },
                child: Builder(
                  builder: (context) {
                    FocusNode node = Focus.of(context);
                    bool isFocused = node.hasFocus;
                    return ListTile(
                      title: Text(
                        AppLocalizations.of(context)!.back,
                        style: TextStyle(color: Colors.white, fontSize: 24),
                      ),
                      tileColor: isFocused
                          ? Theme.of(context).colorScheme.primary
                          : null,
                    );
                  },
                ),
              ),

              Focus(
                autofocus: true,
                onKeyEvent: (node, event) {
                  if (event is KeyDownEvent) {
                    final key = event.logicalKey;
                    if (key == LogicalKeyboardKey.accept ||
                        key == LogicalKeyboardKey.select ||
                        key == LogicalKeyboardKey.enter) {
                      Navigator.pop(context);
                      _goUpdate();
                      return KeyEventResult.handled;
                    }
                  }
                  return KeyEventResult.ignored;
                },
                child: Builder(
                  builder: (context) {
                    FocusNode node = Focus.of(context);
                    bool isFocused = node.hasFocus;
                    return ListTile(
                      title: Text(
                        AppLocalizations.of(context)!.downloadLatestVersion,
                        style: TextStyle(color: Colors.white, fontSize: 24),
                      ),
                      tileColor: isFocused
                          ? Theme.of(context).colorScheme.primary
                          : null,
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _goUpdate() {
    final model = context.read<AppModel>();
    Navigator.of(context)
        .push(MaterialPageRoute(builder: (context) => UpdateScreen()))
        .then((_) => model.loadData());
  }

  Future<void> _handleLogin() async {
    final appModel = context.read<AppModel>();
    var serverUrl = _serverController.text.trim();
    final user = _userController.text;
    final pw = _passController.text;
    if (serverUrl.isEmpty || user.isEmpty || pw.isEmpty || _isLoading) {
      return;
    }
    final lower = serverUrl.toLowerCase();
    if (!lower.startsWith('http://') && !lower.startsWith('https://')) {
      serverUrl = 'http://$serverUrl';
    }
    final loginFailedLabel = AppLocalizations.of(context)!.loginFailed;
    try {
      setState(() {
        _isLoading = true;
      });

      await appModel.login(serverUrl, user, pw);
    } catch (e) {
      showErrorMsg('$loginFailedLabel: $e');
    }
    setState(() {
      _isLoading = false;
    });
  }
}
