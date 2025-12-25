// app_model.dart
import 'package:emby_tv/emby_service.dart';
import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

class AppModel with ChangeNotifier {
  final SharedPreferences _prefs;
  String? _serverUrl;
  String? _apiKey;
  String? _userId;
  String _deviceId = "";
  bool _isLoaded = false;
  String _currentVersion = '';
  String _newVersion = '';
  String _downloadUrl = '';

  List<dynamic> _resumeItems = [];
  List<dynamic> _libraryLatestItems = [];

  List<dynamic> get resumeItems => _resumeItems;
  List<dynamic> get libraryLatestItems => _libraryLatestItems;

  String? get serverUrl => _serverUrl;
  String? get apiKey => _apiKey;
  bool get isLoaded => _isLoaded;

  String get currentVersion => _currentVersion;
  String get newVersion => _newVersion;
  String get downloadUrl => _downloadUrl;

  AppModel(this._prefs) {
    _loadCredentials();
  }

  bool get needUpdate {
    if (_newVersion == '' || _currentVersion == '') return false;
    return _newVersion != _currentVersion;
  }

  bool get isLoggedIn => _apiKey != null && _userId != null;

  Future<void> checkUpdate() async {
    if (_newVersion != '') return;
    final info = await PackageInfo.fromPlatform();
    _currentVersion = 'v${info.version}';
    notifyListeners();
    Map res = await EmbyService.getNewVersion();
    if (res['tag_name'] != null && res['assets'] != null) {
      _newVersion = res['tag_name'] ?? '';
      List assets = res['assets'] ?? [];
      if (assets.isNotEmpty) {
        _downloadUrl = assets[0]['browser_download_url'] ?? '';
      }
    }
    notifyListeners();
  }

  Future<void> _loadCredentials() async {
    final savedServerUrl = _prefs.getString('serverUrl');
    final savedApiKey = _prefs.getString('apiKey');
    final savedUserId = _prefs.getString('userId');
    await getDeviceId();

    if (savedServerUrl != null && savedApiKey != null && savedUserId != null) {
      try {
        final service = EmbyService(savedServerUrl, '', _deviceId);
        final testResponse = await service.testKey(savedUserId, savedApiKey);

        if (testResponse.statusCode == 200) {
          _serverUrl = savedServerUrl;
          _apiKey = savedApiKey;
          _userId = savedUserId;

          await loadData();
          notifyListeners();
        } else {
          await _clearCredentials();
        }
      } catch (e) {
        await _clearCredentials();
      }
    }
    _isLoaded = true;
    notifyListeners();
  }

  Future<void> _clearCredentials() async {
    await _prefs.remove('apiKey');
    await _prefs.remove('userId');
    _apiKey = null;
    _userId = null;
    notifyListeners();
  }

  Future<void> getDeviceId() async {
    SharedPreferences prefs = await SharedPreferences.getInstance();
    String? id = prefs.getString("deviceId");
    if (id == null) {
      id = 'flutter_tv_${DateTime.now().millisecondsSinceEpoch}';
      await prefs.setString("deviceId", id);
    }
    _deviceId = id;
    notifyListeners();
  }

  Future<void> logout() async {
    await _clearCredentials();
    _resumeItems = [];
    _libraryLatestItems = [];
    notifyListeners();
  }

  Future<void> login(String serverUrl, String username, String password) async {
    await _prefs.setString('serverUrl', serverUrl);
    await _prefs.setString('username', username);
    await _prefs.setString('password', password);
    final service = EmbyService(serverUrl, '', _deviceId);
    final response = await service.authenticate(username, password);

    _serverUrl = serverUrl;
    _apiKey = response['AccessToken'];
    _userId = response['User']['Id'];

    await _prefs.setString('apiKey', _apiKey!);
    await _prefs.setString('userId', _userId!);

    await loadData();
    notifyListeners();
  }

  Future<void> loadData() async {
    _resumeItems = await EmbyService(
      _serverUrl!,
      _apiKey!,
      _deviceId,
    ).getResumeItems(_userId!);
    _libraryLatestItems = await EmbyService(
      _serverUrl!,
      _apiKey!,
      _deviceId,
    ).getLatestItems(_userId!);
    notifyListeners();
  }

  Future<Map> getMediaInfo(String id) async {
    Map res = await EmbyService(
      _serverUrl!,
      _apiKey!,
      _deviceId,
    ).getMediaInfo(_userId!, id);
    return res;
  }

  Future<List> getLibraryList(String parentId, String type) async {
    List res = await EmbyService(
      _serverUrl!,
      _apiKey!,
      _deviceId,
    ).getLibraryList(_userId!, parentId, type);
    return res;
  }

  Future<List> getSeriesList(String parentId) async {
    List res = await EmbyService(
      _serverUrl!,
      _apiKey!,
      _deviceId,
    ).getSeriesList(_userId!, parentId);
    return res;
  }

  Future<List> getShowsNextUp(String parentId) async {
    List res = await EmbyService(
      _serverUrl!,
      _apiKey!,
      _deviceId,
    ).getShowsNextUp(_userId!, parentId);
    return res;
  }

  Future<List> getSeasonList(String parentId) async {
    List res = await EmbyService(
      _serverUrl!,
      _apiKey!,
      _deviceId,
    ).getSeasonList(_userId!, parentId);
    return res;
  }

  Future<String> getSubtitle(
    String itemId,
    String id,
    int selectedSubtitleIndex,
    String codec,
  ) async {
    String res = await EmbyService(
      _serverUrl!,
      _apiKey!,
      _deviceId,
    ).getSubtitle(itemId, id, selectedSubtitleIndex, codec);
    return res;
  }

  Future<Map> getPlaybackInfo(
    String id,
    int playbackPositionTicks,
    bool isSeries, {
    bool disableHevc = false,
  }) async {
    Map res = {};
    if (isSeries) {
      res = await EmbyService(_serverUrl!, _apiKey!, _deviceId)
          .getShowsNextBackInfo(
            _userId!,
            id,
            playbackPositionTicks,
            disableHevc: disableHevc,
          );
    } else {
      res = await EmbyService(_serverUrl!, _apiKey!, _deviceId).getPlaybackInfo(
        _userId!,
        id,
        playbackPositionTicks,
        disableHevc: disableHevc,
      );
    }
    return res;
  }

  Future<void> reportPlaybackProgress(Map body) async {
    await EmbyService(
      _serverUrl!,
      _apiKey!,
      _deviceId,
    ).reportPlaybackProgress(body);
  }

  Future<void> stoped(Map body) async {
    await EmbyService(_serverUrl!, _apiKey!, _deviceId).stoped(body);
  }

  Future<void> playing(Map body) async {
    await EmbyService(_serverUrl!, _apiKey!, _deviceId).playing(body);
  }

  Future<List> getPlayingSessions() async {
    final res = await EmbyService(
      _serverUrl!,
      _apiKey!,
      _deviceId,
    ).getSessions();
    return res;
  }
}
