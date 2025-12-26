// emby_service.dart
import 'dart:convert';
import 'package:emby_tv/utils.dart';
import 'package:http/http.dart';

class EmbyService {
  static const String _client = 'Android TV';
  final String _serverUrl;
  final String _apiKey;
  final String _deviceId;

  EmbyService(this._serverUrl, this._apiKey, this._deviceId);

  static Future<Map> getNewVersion() async {
    final headers = {"content-type": "application/json; charset=utf-8"};
    try {
      final response = await get(
        Uri.parse(
          'https://api.github.com/repos/shareven/emby_tv/releases/latest',
        ),
        headers: headers,
      );
      if (response.statusCode == 200) {
        return jsonDecode(response.body);
      } else {
        return {};
      }
    } catch (e) {
      return {};
    }
  }

  Future<Response> http(String url, {method = 'GET', body}) async {
    Response response;
    try {
      final headers = {"content-type": "application/json; charset=utf-8"};
      final params =
          '&X-Emby-Client=$_client&X-Emby-Client-Version=1.0.0&X-Emby-Device-Name=Android TV&X-Emby-Device-Id=$_deviceId';
      if (method == 'GET') {
        response = await get(
          Uri.parse('$_serverUrl/emby$url$params'),
          headers: headers,
        );
      } else {
        response = await post(
          Uri.parse('$_serverUrl/emby$url$params'),
          body: jsonEncode(body),
          headers: headers,
        );
      }
    } catch (e) {
      response = Response(e.toString(), 500);
    }
    if (response.statusCode == 500) {}
    return response;
  }

  Future<Map<String, dynamic>> authenticate(
    String username,
    String password,
  ) async {
    final response = await http(
      '/Users/authenticatebyname?xx=1',
      method: 'POST',
      body: ({'Username': username, 'Pw': password}),
    );
    if (response.statusCode == 500) {
      showErrorMsg(response.body);
    }
    return jsonDecode(response.body);
  }

  Future<dynamic> testKey(String savedUserId, String savedApiKey) async {
    final response = await http(
      '/Users/$savedUserId?X-Emby-Token=$savedApiKey',
    );

    return response;
  }

  Future<List<dynamic>> getResumeItems(String userId) async {
    final response = await http(
      '/Users/$userId/Items/Resume?Limit=15&MediaTypes=Video&Fields=BasicSyncInfo,CanDelete,PrimaryImageAspectRatio,ProductionYear&X-Emby-Token=$_apiKey',
    );

    return jsonDecode(response.body)['Items'];
  }

  Future<List> getShowsNextUp(String userId, String seriesId) async {
    final response = await http(
      '/Shows/NextUp?SeriesId=$seriesId&UserId=$userId&EnableTotalRecordCount=false&ExcludeLocationTypes=Virtual&Fields=ProductionYear%2CPremiereDate%2CContainer&X-Emby-Token=$_apiKey',
    );

    return jsonDecode(response.body)['Items'];
  }

  Future<Map> getShowsNextBackInfo(
    String userId,
    String seriesId,
    int startTimeTicks, {
    bool disableHevc = false,
  }) async {
    final response = await http(
      '/Shows/NextUp?SeriesId=$seriesId&UserId=$userId&EnableTotalRecordCount=false&ExcludeLocationTypes=Virtual&Fields=ProductionYear%2CPremiereDate%2CContainer&X-Emby-Token=$_apiKey',
    );

    final data = jsonDecode(response.body);
    var items = (data['Items'] as List?) ?? const [];
    if (items.isEmpty) {
      items = await getSeriesList(userId, seriesId);
    }
    final first = items.first;
    final id = first is Map ? first['Id']?.toString() ?? '' : '';
    if (id.isEmpty) {
      return getPlaybackInfo(
        userId,
        seriesId,
        startTimeTicks,
        disableHevc: disableHevc,
      );
    }
    return getPlaybackInfo(
      userId,
      id,
      startTimeTicks,
      disableHevc: disableHevc,
    );
  }

  Future<Map> getPlaybackInfo(
    String userId,
    String mediaId,
    int startTimeTicks, {
    bool disableHevc = false,
  }) async {
    final body = await buildPlaybackInfoBody(disableHevc: disableHevc);
    print(body.toString());
    final response = await http(
      '/Items/$mediaId/PlaybackInfo?UserId=$userId&StartTimeTicks=$startTimeTicks&IsPlayback=true&AutoOpenLiveStream=true&MaxStreamingBitrate=60000000&X-Emby-Token=$_apiKey&X-Emby-Language=zh-cn&reqformat=json',
      method: "POST",
      body: body,
    );

    final text = response.body.trimLeft();
    if (text.isEmpty ||
        (!text.startsWith('{') &&
            !text.startsWith('[') &&
            !text.startsWith('"'))) {
      showErrorMsg('Failed to get playback info: ${response.statusCode}');
      return {};
    }
    try {
      final decoded = jsonDecode(text);
      if (decoded is Map) {
        return decoded;
      }
      showErrorMsg('Invalid playback info format');
      return {};
    } on FormatException {
      showErrorMsg('Failed to parse playback info');
      return {};
    }
  }

  Future<List<dynamic>> getViews(String userId) async {
    final response = await http('/Users/$userId/Views?X-Emby-Token=$_apiKey');

    return jsonDecode(response.body)['Items'];
  }

  Future<List<dynamic>> getLibraryList(
    String userId,
    String parentId,
    String type,
  ) async {
    final response = await http(
      '/Users/$userId/Items?IncludeItemTypes=$type&Fields=BasicSyncInfo%2CCanDelete%2CPrimaryImageAspectRatio%2CProductionYear%2CStatus%2CEndDate&StartIndex=0&SortBy=SortName&SortOrder=Ascending&ParentId=$parentId&EnableImageTypes=Primary%2CBackdrop%2CThumb&ImageTypeLimit=1&Recursive=true&Limit=2000&X-Emby-Token=$_apiKey',
    );
    return jsonDecode(response.body)['Items'];
  }

  Future<Map> getMediaInfo(String userId, String mediaId) async {
    final response = await http(
      '/Users/$userId/Items/$mediaId?Fields=PrimaryImageAspectRatio%2COverview%2CGenres%2CStudios%2CTaglines%2CProductionYear%2CPremiereDate%2CEndDate%2CRunTimeTicks%2CCommunityRating%2CCriticRating%2COfficialRating%2CPeople%2CProviderIds%2CStatus%2CPath&ExcludeFields=VideoChapters&X-Emby-Token=$_apiKey',
    );
    return jsonDecode(response.body);
  }

  Future<List> getSeriesList(String userId, String parentId) async {
    final response = await http(
      '/Users/$userId/Items?UserId=$userId&Fields=BasicSyncInfo%2CCanDelete%2CPrimaryImageAspectRatio%2COverview%2CPremiereDate%2CProductionYear%2CRunTimeTicks%2CSpecialEpisodeNumbers&Recursive=true&IsFolder=false&ParentId=$parentId&Limit=1000&X-Emby-Token=$_apiKey',
    );
    return jsonDecode(response.body)['Items'];
  }

  Future<List> getSeasonList(String userId, String parentId) async {
    final response = await http(
      '/Shows/$parentId/Seasons?UserId=$userId&Fields=BasicSyncInfo%2CCanDelete%2CPrimaryImageAspectRatio&Limit=100&X-Emby-Token=$_apiKey',
    );
    return jsonDecode(response.body)['Items'];
  }

  Future<String> getSubtitle(
    String itemId,
    String id,
    int selectedSubtitleIndex,
    String codec,
  ) async {
    final response = await http(
      '/Videos/$itemId/$id/Subtitles/$selectedSubtitleIndex/0/Stream.$codec?api_key=$_apiKey',
    );
    return utf8.decode(response.bodyBytes, allowMalformed: true);
  }

  Future<List<dynamic>> getLatestItemsByViews(
    String userId,
    String parentId,
  ) async {
    final response = await http(
      '/Users/$userId/Items/Latest?Limit=20&ParentId=$parentId&Fields=BasicSyncInfo,CanDelete,PrimaryImageAspectRatio,ProductionYear&X-Emby-Token=$_apiKey',
    );
    return jsonDecode(response.body);
  }

  Future<List<dynamic>> getSessions() async {
    final response = await http(
      '/Sessions?deviceId=$_deviceId&X-Emby-Device-Id=$_deviceId&X-Emby-Token=$_apiKey',
    );
    return jsonDecode(response.body);
  }

  Future<List<dynamic>> getLatestItems(String userId) async {
    List list = await getViews(userId);
    List resList = await Future.wait(
      list.map((library) async {
        final items = await getLatestItemsByViews(userId, library['Id']);
        library['latestItems'] = items;
        return library;
      }),
    );
    return resList;
  }

  Future<void> reportPlaybackProgress(dynamic body) async {
    await http(
      '/Sessions/Playing/Progress?X-Emby-Token=$_apiKey',
      method: 'POST',
      body: body,
    );
  }

  Future playing(Map body) async {
    await http(
      '/Sessions/Playing?reqformat=json&X-Emby-Token=$_apiKey',
      method: 'POST',
      body: body,
    );
  }

  Future stoped(Map body) async {
    await http(
      '/Sessions/Playing/Stopped?reqformat=json&X-Emby-Token=$_apiKey',
      method: 'POST',
      body: body,
    );
  }
}
