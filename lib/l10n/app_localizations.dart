import 'package:flutter/widgets.dart';

class AppLocalizations {
  final Locale locale;
  AppLocalizations(this.locale);

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  static final Map<String, Map<String, String>> _localizedValues = {
    'en': {
      'newVersionAvailable': '( New version available: {version})',
      'info': 'Info',
      'server_url': 'Server URL',
      'username': 'Username',
      'password': 'Password',
      'logging_in': 'Logging in...',
      'login': 'Login',
      'login_failed': 'Login failed',
      'back': 'Back',
      'logout': 'Logout',
      'download_latest_version': 'Download latest version',
      'my_libraries': 'My libraries',
      'continue_watching': 'Continue watching',
      'press_menu_to_show_menu': 'Press menu key to show menu',
      'press_menu_down_to_show_menu': 'Press menu key or down key to show menu',
      'resume': 'Resume',
      'play': 'Play',
      'details': 'Details',
      'genres': 'Genres',
      'studios': 'Studios',
      'premiere': 'Premiere',
      'end': 'End',
      'status': 'Status',
      'tagline': 'Tagline',
      'cast': 'Cast',
      'provider_ids': 'Provider IDs',
      'path': 'Path',
      'counts': 'Counts',
      'seasons': 'Seasons',
      'season': 'Season',
      'unknown': 'Unknown',
      'failed_get_playback_info': 'Failed to get playback info',
      'streaming': 'Streaming',
      'video': 'Video',
      'audio_label': 'Audio',
      'episodes': 'Episodes',
      'playback': 'Playback',
      'subtitles': 'Subtitles',
      'audio': 'Audio',
      'disable_subtitles': 'Disable subtitles',
      'no_episodes_found': 'No episodes found',
      'list_loop': 'List loop',
      'single_loop': 'Single loop',
      'no_loop': 'No loop',
      'direct_play': 'Direct play',
      'directStream': 'Direct stream',
      'transcode': 'Transcode',
      'hardware_acceleration': 'Hardware acceleration',
      'stereo': 'stereo',
      'default_marker': '(default)',
      'fps_suffix': 'fps',
      'playback_correction': 'Playback correction',
      'playback_correction_off': 'Off',
      'playback_correction_server': 'Server transcode to H.264',
      'footer_notice':
          'This is a free open-source Emby client for learning and technical exchange; commercial use is prohibited.\nRepository: https://github.com/shareven/emby_tv',
      'transcode_reason_video_codec': 'Video codec not supported',
      'transcode_reason_audio_codec': 'Audio codec not supported',
      'transcode_reason_container': 'Container not supported',
      'transcode_reason_bitrate': 'Bitrate too high',
      'transcode_reason_resolution': 'Resolution too high',
      'transcode_reason_framerate': 'Framerate not supported',
      'transcode_reason_subtitle': 'Subtitle not supported',
      'transcode_reason_direct_play_blocked': 'Direct play not allowed',
      'transcode_reason_unknown': 'Transcode reason',
    },
    'zh': {
      'newVersionAvailable': ' （新版本可用: {version}）',
      'info': '信息',
      'server_url': '服务器地址',
      'username': '用户名',
      'password': '密码',
      'logging_in': '登录中...',
      'login': '登录',
      'login_failed': '登录失败',
      'back': '返回',
      'logout': '退出登录',
      'download_latest_version': '下载最新版本',
      'my_libraries': '我的媒体库',
      'continue_watching': '继续观看',
      'press_menu_to_show_menu': '按菜单键打开菜单',
      'press_menu_down_to_show_menu': '按菜单键或下方向键打开菜单',
      'resume': '继续',
      'play': '播放',
      'details': '详情',
      'genres': '类型',
      'studios': '制作公司',
      'premiere': '首播',
      'end': '结束',
      'status': '状态',
      'tagline': '标语',
      'cast': '演员',
      'provider_ids': '提供者 ID',
      'path': '路径',
      'counts': '统计',
      'seasons': '季',
      'season': '季',
      'unknown': '未知',
      'failed_get_playback_info': '获取播放信息失败',
      'streaming': '流媒体',
      'video': '视频',
      'audio_label': '音频',
      'episodes': '选集',
      'playback': '播放',
      'subtitles': '字幕',
      'audio': '音频',
      'disable_subtitles': '关闭字幕',
      'no_episodes_found': '未找到分集',
      'list_loop': '列表循环',
      'single_loop': '单集循环',
      'no_loop': '不循环',
      'direct_play': '直接播放',
      'directStream': '直接流处理',
      'transcode': '转码',
      'hardware_acceleration': '硬件加速',
      'stereo': '立体声',
      'default_marker': '(默认)',
      'fps_suffix': 'fps',
      'playback_correction': '播放校正',
      'playback_correction_off': '关闭',
      'playback_correction_server': '服务器转码为H.264',
      'footer_notice':
          '这是一个用于学习和技术交流的开源免费 Emby 客户端，禁止商业用途。\n开源地址：https://github.com/shareven/emby_tv',
      'transcode_reason_video_codec': '视频编码不被支持',
      'transcode_reason_audio_codec': '音频编码不被支持',
      'transcode_reason_container': '容器格式不被支持',
      'transcode_reason_bitrate': '码率过高',
      'transcode_reason_resolution': '分辨率过高',
      'transcode_reason_framerate': '帧率不被支持',
      'transcode_reason_subtitle': '字幕不被支持',
      'transcode_reason_direct_play_blocked': '不允许直接播放',
      'transcode_reason_unknown': '转码原因',
    },
  };

  String _get(String key) {
    final lang = locale.languageCode;
    return _localizedValues[lang]?[key] ?? _localizedValues['en']![key] ?? key;
  }

  String newVersionAvailable(String version) =>
      _get('newVersionAvailable').replaceAll('{version}', version);
  String get info => _get('info');
  String get serverUrl => _get('server_url');
  String get username => _get('username');
  String get password => _get('password');
  String get loggingIn => _get('logging_in');
  String get login => _get('login');
  String get loginFailed => _get('login_failed');
  String get back => _get('back');
  String get logout => _get('logout');
  String get downloadLatestVersion => _get('download_latest_version');
  String get myLibraries => _get('my_libraries');
  String get continueWatching => _get('continue_watching');
  String get pressMenuToShowMenu => _get('press_menu_to_show_menu');
  String get pressMenuDownToShowMenu => _get('press_menu_down_to_show_menu');
  String get resume => _get('resume');
  String get play => _get('play');
  String get details => _get('details');
  String get genres => _get('genres');
  String get studios => _get('studios');
  String get premiere => _get('premiere');
  String get end => _get('end');
  String get status => _get('status');
  String get tagline => _get('tagline');
  String get cast => _get('cast');
  String get providerIds => _get('provider_ids');
  String get path => _get('path');
  String get counts => _get('counts');
  String get seasonsLabel => _get('seasons');
  String get season => _get('season');
  String get unknown => _get('unknown');
  String get failedGetPlaybackInfo => _get('failed_get_playback_info');
  String get streaming => _get('streaming');
  String get videoLabel => _get('video');
  String get audioLabel => _get('audio_label');
  String get episodes => _get('episodes');
  String get playback => _get('playback');
  String get subtitles => _get('subtitles');
  String get playbackCorrection => _get('playback_correction');
  String get playbackCorrectionOff => _get('playback_correction_off');
  String get playbackCorrectionServer => _get('playback_correction_server');
  String get audio => _get('audio');
  String get disableSubtitles => _get('disable_subtitles');
  String get noEpisodesFound => _get('no_episodes_found');
  String get transcodeReasonUnknown => _get('transcode_reason_unknown');

  String transcodeReason(String reason) {
    switch (reason) {
      case 'VideoCodecNotSupported':
      case 'VideoCodecNotSupportedByDevice':
        return _get('transcode_reason_video_codec');
      case 'AudioCodecNotSupported':
      case 'AudioCodecNotSupportedByDevice':
        return _get('transcode_reason_audio_codec');
      case 'ContainerNotSupported':
        return _get('transcode_reason_container');
      case 'BitrateTooHigh':
        return _get('transcode_reason_bitrate');
      case 'ResolutionTooHigh':
        return _get('transcode_reason_resolution');
      case 'FramerateNotSupported':
        return _get('transcode_reason_framerate');
      case 'SubtitleNotSupported':
        return _get('transcode_reason_subtitle');
      case 'DirectPlayNotAllowed':
        return _get('transcode_reason_direct_play_blocked');
      case 'ContainerBitrateExceedsLimit':
        return _get('transcode_reason_bitrate');
      default:
        return reason;
    }
  }

  String get listLoop => _get('list_loop');
  String get singleLoop => _get('single_loop');
  String get noLoop => _get('no_loop');
  String get directPlay => _get('direct_play');
  String get directStream => _get('directStream');
  String get transcode => _get('transcode');
  String get hardwareAcceleration => _get('hardware_acceleration');
  String get stereo => _get('stereo');
  String get defaultMarker => _get('default_marker');
  String get fpsSuffix => _get('fps_suffix');
  String get footerNotice => _get('footer_notice');
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  bool isSupported(Locale locale) => ['en', 'zh'].contains(locale.languageCode);

  @override
  Future<AppLocalizations> load(Locale locale) async {
    return AppLocalizations(locale);
  }

  @override
  bool shouldReload(covariant LocalizationsDelegate<AppLocalizations> old) =>
      false;
}
