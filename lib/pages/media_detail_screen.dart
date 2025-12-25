import 'package:cached_network_image/cached_network_image.dart';
import 'package:emby_tv/app_model.dart';
import 'package:emby_tv/pages/player_screen.dart';
import 'package:emby_tv/utils.dart';
import 'package:emby_tv/widgets/build_gradient_background.dart';
import 'package:emby_tv/widgets/build_item.dart';
import 'package:emby_tv/widgets/loading.dart';
import 'package:emby_tv/widgets/no_data.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:emby_tv/l10n/app_localizations.dart';

class MediaDetailScreen extends StatefulWidget {
  final String mediaId;
  const MediaDetailScreen({required this.mediaId, super.key});

  @override
  State<MediaDetailScreen> createState() => _MediaDetailScreenState();
}

class _MediaDetailScreenState extends State<MediaDetailScreen> {
  Map? _mediaInfo;
  List? _seasons;
  List? _episodes;
  List? _nextUp;
  int _selectedSeasonIndex = 0;
  final GlobalKey _autoFocusKey = GlobalKey();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _load();
    });
  }

  Future<void> _load() async {
    final model = context.read<AppModel>();
    final info = await model.getMediaInfo(widget.mediaId);
    if (!mounted) return;
    final type = (info['Type'] ?? '').toString();
    if (type == 'Series') {
      final list = await Future.wait([
        model.getSeasonList(widget.mediaId),
        model.getSeriesList(widget.mediaId),
        model.getShowsNextUp(widget.mediaId),
      ]);
      if (!mounted) return;
      setState(() {
        _mediaInfo = info;
        _seasons = list[0] as List?;
        _episodes = list[1] as List?;
        _nextUp = list[2] as List?;
        _selectedSeasonIndex = 0;
      });
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_autoFocusKey.currentContext != null) {
          Scrollable.ensureVisible(_autoFocusKey.currentContext!);
        }
      });
      return;
    }
    setState(() {
      _mediaInfo = info;
      _seasons = null;
      _episodes = null;
      _nextUp = null;
      _selectedSeasonIndex = 0;
    });
  }

  String _backdropUrl(String serverUrl, Map item) {
    final tags = item['BackdropImageTags'];
    if (tags is List && tags.isNotEmpty && tags[0] != null) {
      return '$serverUrl/emby/Items/${item['Id']}/Images/Backdrop?maxWidth=1280&tag=${tags[0]}&quality=80';
    }
    if (item['ParentBackdropItemId'] != null &&
        item['ParentBackdropImageTags'] is List &&
        (item['ParentBackdropImageTags'] as List).isNotEmpty) {
      final t = (item['ParentBackdropImageTags'] as List).first;
      return '$serverUrl/emby/Items/${item['ParentBackdropItemId']}/Images/Backdrop?maxWidth=1280&tag=$t&quality=80';
    }
    return '';
  }

  String _formatRuntimeFromTicks(dynamic ticks) {
    final v = ticks is int ? ticks : int.tryParse(ticks?.toString() ?? '');
    if (v == null || v <= 0) return '';
    final totalMinutes = (v / 10000000 / 60).round();
    if (totalMinutes <= 0) return '';
    final h = totalMinutes ~/ 60;
    final m = totalMinutes % 60;
    if (h <= 0) return '${m}m';
    if (m == 0) return '${h}h';
    return '${h}h ${m}m';
  }

  String _formatDate(dynamic raw) {
    final s = raw?.toString() ?? '';
    if (s.isEmpty) return '';
    final dt = DateTime.tryParse(s);
    if (dt == null) return '';
    final mm = dt.month.toString().padLeft(2, '0');
    final dd = dt.day.toString().padLeft(2, '0');
    return '${dt.year}-$mm-$dd';
  }

  String _joinList(dynamic list) {
    if (list is List) {
      return list
          .map((e) => e.toString())
          .where((e) => e.isNotEmpty)
          .join(', ');
    }
    return '';
  }

  String _formatProviderIds(dynamic ids) {
    if (ids is Map) {
      final parts = <String>[];
      for (final entry in ids.entries) {
        final k = entry.key?.toString() ?? '';
        final v = entry.value?.toString() ?? '';
        if (k.isEmpty || v.isEmpty) continue;
        parts.add('$k: $v');
      }
      return parts.join(' · ');
    }
    return '';
  }

  List<Map> _seasonEpisodes(String seasonName) {
    final items = _episodes;
    if (items is! List) return const [];
    final res = <Map>[];
    for (final e in items) {
      if (e is Map && e['SeasonName'] == seasonName) {
        res.add(e);
      }
    }
    return res;
  }

  void _openPlayerForItem(Map item) {
    final ticks = item['UserData']?['PlaybackPositionTicks'] ?? 0;
    Navigator.of(context)
        .push(
          MaterialPageRoute(
            builder: (context) => PlayerScreen(
              mediaId: item['Id'],
              isSeries: (item['Type'] ?? '') == 'Series',
              playbackPositionTicks: ticks is int ? ticks : 0,
            ),
          ),
        )
        .then((_) => _load());
  }

  void _onPrimaryAction() {
    final info = _mediaInfo;
    if (info == null) return;
    final type = (info['Type'] ?? '').toString();
    if (type == 'Series') {
      if (_nextUp is List && (_nextUp as List).isNotEmpty) {
        final first = (_nextUp as List).first;
        if (first is Map) {
          _openPlayerForItem(first);
          return;
        }
      }
      final seasons = _seasons;
      if (seasons is List && seasons.isNotEmpty) {
        final name = (seasons.first is Map)
            ? (seasons.first as Map)['Name']?.toString() ?? ''
            : '';
        if (name.isNotEmpty) {
          final eps = _seasonEpisodes(name);
          if (eps.isNotEmpty) {
            _openPlayerForItem(eps.first);
            return;
          }
        }
      }
      return;
    }
    final ticks = info['UserData']?['PlaybackPositionTicks'] ?? 0;
    Navigator.of(context)
        .push(
          MaterialPageRoute(
            builder: (context) => PlayerScreen(
              mediaId: widget.mediaId,
              isSeries: false,
              playbackPositionTicks: ticks is int ? ticks : 0,
            ),
          ),
        )
        .then((_) => _load());
  }

  Widget _buildMetaRow(String label, String value) {
    if (value.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.7),
                fontSize: 13,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(color: Colors.white, fontSize: 13),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPrimaryButton({required bool isResume}) {
    return Focus(
      autofocus: true,
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent) {
          final key = event.logicalKey;
          if (key == LogicalKeyboardKey.select ||
              key == LogicalKeyboardKey.accept ||
              key == LogicalKeyboardKey.enter) {
            _onPrimaryAction();
            return KeyEventResult.handled;
          }
        }
        return KeyEventResult.ignored;
      },
      child: Builder(
        builder: (context) {
          final node = Focus.of(context);
          final isFocused = node.hasFocus;
          final color = isFocused
              ? Theme.of(context).colorScheme.primary
              : Colors.white24;
          return GestureDetector(
            onTap: () {
              node.requestFocus();
              _onPrimaryAction();
            },
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
              decoration: BoxDecoration(
                color: color,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Text(
                isResume
                    ? AppLocalizations.of(context)!.resume
                    : AppLocalizations.of(context)!.play,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final model = context.watch<AppModel>();
    final serverUrl = model.serverUrl ?? '';
    final info = _mediaInfo;
    if (info == null) {
      return const Scaffold(body: BuildGradientBackground(child: Loading()));
    }

    final backdrop = serverUrl.isNotEmpty ? _backdropUrl(serverUrl, info) : '';
    final poster = serverUrl.isNotEmpty
        ? getImageUrl(serverUrl, info, false)
        : '';
    final title = (info['Name'] ?? '').toString();
    final overview = (info['Overview'] ?? '').toString();
    final year = (info['ProductionYear'] ?? '').toString();
    final runtime = _formatRuntimeFromTicks(info['RunTimeTicks']);
    final officialRating = (info['OfficialRating'] ?? '').toString();
    final communityRating = info['CommunityRating']?.toString() ?? '';
    final genres = _joinList(info['Genres']);
    final studios = (info['Studios'] is List)
        ? (info['Studios'] as List)
              .whereType<Map>()
              .map((e) => e['Name']?.toString() ?? '')
              .where((e) => e.isNotEmpty)
              .join(', ')
        : '';
    final premiere = _formatDate(info['PremiereDate']);
    final endDate = _formatDate(info['EndDate']);
    final taglines = _joinList(info['Taglines']);
    final people = (info['People'] is List)
        ? (info['People'] as List)
              .whereType<Map>()
              .where((p) => (p['Type'] ?? '').toString() == 'Actor')
              .take(12)
              .map((p) => p['Name']?.toString() ?? '')
              .where((e) => e.isNotEmpty)
              .join(', ')
        : '';
    final playbackTicks = info['UserData']?['PlaybackPositionTicks'];
    final isResume = playbackTicks is int && playbackTicks > 0;
    final type = (info['Type'] ?? '').toString();
    final status = (info['Status'] ?? '').toString();
    final path = (info['Path'] ?? '').toString();
    final providerIds = _formatProviderIds(info['ProviderIds']);
    final played = info['UserData']?['Played'] == true;
    final favorite = info['UserData']?['IsFavorite'] == true;
    final userStateParts = <String>[];
    if (favorite) userStateParts.add('Favorite');
    if (played) userStateParts.add('Played');
    if (isResume) userStateParts.add('In progress');

    final seasons = _seasons;
    final hasSeasons = seasons is List && seasons.isNotEmpty;
    final selectedSeasonName = hasSeasons
        ? ((seasons[_selectedSeasonIndex] is Map)
              ? (seasons[_selectedSeasonIndex] as Map)['Name']?.toString() ?? ''
              : '')
        : '';
    final seasonEpisodes = selectedSeasonName.isNotEmpty
        ? _seasonEpisodes(selectedSeasonName)
        : const <Map>[];

    double height = 290;
    List aspectRatios = seasonEpisodes
        .map((e) => (e["PrimaryImageAspectRatio"] ?? 1).toDouble())
        .toList();
    double maxAspectRatio = aspectRatios.isEmpty
        ? 1
        : aspectRatios.reduce(
            (currMax, ratio) => ratio > currMax ? ratio : currMax,
          );
    double width = maxAspectRatio * height - (maxAspectRatio > 1 ? 200 : 90);
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          if (backdrop.isNotEmpty)
            Positioned.fill(
              child: CachedNetworkImage(imageUrl: backdrop, fit: BoxFit.cover),
            )
          else
            const Positioned.fill(
              child: BuildGradientBackground(child: SizedBox.shrink()),
            ),
          Positioned.fill(
            child: Container(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Colors.black.withValues(alpha: 0.55),
                    Colors.black.withValues(alpha: 0.75),
                    Colors.black.withValues(alpha: 0.92),
                  ],
                ),
              ),
            ),
          ),
          SafeArea(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(18),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(12),
                        child: poster.isEmpty
                            ? Container(
                                width: 160,
                                height: 240,
                                color: Colors.white12,
                                child: const Icon(
                                  Icons.movie,
                                  color: Colors.white54,
                                  size: 50,
                                ),
                              )
                            : CachedNetworkImage(
                                width: 160,
                                height: 240,
                                imageUrl: poster,
                                fit: BoxFit.cover,
                              ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              title,
                              style: const TextStyle(
                                fontSize: 28,
                                fontWeight: FontWeight.bold,
                                color: Colors.white,
                                shadows: [
                                  Shadow(
                                    color: Colors.black54,
                                    blurRadius: 8,
                                    offset: Offset(2, 2),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(height: 8),
                            Wrap(
                              spacing: 10,
                              runSpacing: 8,
                              children: [
                                if (year.isNotEmpty) _pill(year),
                                if (runtime.isNotEmpty) _pill(runtime),
                                if (officialRating.isNotEmpty)
                                  _pill(officialRating),
                                if (communityRating.isNotEmpty)
                                  _pill('★ $communityRating'),
                                if (type.isNotEmpty) _pill(type),
                              ],
                            ),
                            const SizedBox(height: 14),
                            Row(
                              children: [
                                _buildPrimaryButton(isResume: isResume),
                              ],
                            ),
                            if (overview.isNotEmpty) ...[
                              const SizedBox(height: 14),
                              Text(
                                overview,
                                maxLines: 6,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  color: Colors.white.withValues(alpha: 0.9),
                                  fontSize: 13,
                                  height: 1.35,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 18),
                  if (type == 'Series') ...[
                    const SizedBox(height: 18),
                    if (hasSeasons)
                      _buildSeasonSelector(seasons)
                    else
                      const NoData(),
                    const SizedBox(height: 12),
                    if (seasonEpisodes.isNotEmpty)
                      SizedBox(
                        height: height,
                        child: ListView.builder(
                          scrollDirection: Axis.horizontal,
                          padding: const EdgeInsets.symmetric(horizontal: 6),
                          itemCount: seasonEpisodes.length,
                          itemBuilder: (context, index) {
                            final item = seasonEpisodes[index];
                            // final isAutoFocus = _isAutoFocusEpisode(item);

                            return Padding(
                              padding: const EdgeInsets.only(
                                right: 14,
                                bottom: 12,
                              ),
                              child: Focus(
                                key: Key(item['Id']),
                                autofocus: false,
                                onKeyEvent: (node, event) {
                                  if (event is KeyDownEvent) {
                                    final key = event.logicalKey;
                                    if (key == LogicalKeyboardKey.accept ||
                                        key == LogicalKeyboardKey.select ||
                                        key == LogicalKeyboardKey.enter) {
                                      _openPlayerForItem(item);
                                      return KeyEventResult.handled;
                                    }
                                  }
                                  return KeyEventResult.ignored;
                                },
                                child: Builder(
                                  builder: (context) {
                                    final node = Focus.of(context);
                                    final isFocused = node.hasFocus;
                                    return GestureDetector(
                                      onTap: () {
                                        node.requestFocus();
                                        _openPlayerForItem(item);
                                      },
                                      child: BuildItem(
                                        item: item,
                                        width: width,
                                        isContinueWatching: false,
                                        isMyLibrary: false,
                                        isFocused: isFocused,
                                        isShowOverview: true,
                                        imageBoxFit: BoxFit.fitHeight,
                                      ),
                                    );
                                  },
                                ),
                              ),
                            );
                          },
                        ),
                      )
                    else
                      const NoData(),
                  ],
                  const SizedBox(height: 18),
                  Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.35),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: Colors.white10),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          AppLocalizations.of(context)!.details,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 12),
                        _buildMetaRow(
                          AppLocalizations.of(context)!.genres,
                          genres,
                        ),
                        _buildMetaRow(
                          AppLocalizations.of(context)!.studios,
                          studios,
                        ),
                        _buildMetaRow(
                          AppLocalizations.of(context)!.premiere,
                          premiere,
                        ),
                        _buildMetaRow(
                          AppLocalizations.of(context)!.end,
                          endDate,
                        ),
                        _buildMetaRow(
                          AppLocalizations.of(context)!.status,
                          status,
                        ),
                        _buildMetaRow(
                          AppLocalizations.of(context)!.tagline,
                          taglines,
                        ),
                        _buildMetaRow(
                          AppLocalizations.of(context)!.cast,
                          people,
                        ),
                        _buildMetaRow(
                          AppLocalizations.of(context)!.providerIds,
                          providerIds,
                        ),
                        _buildMetaRow(AppLocalizations.of(context)!.path, path),
                        if (type == 'Series')
                          _buildMetaRow(
                            AppLocalizations.of(context)!.counts,
                            '${_seasons?.length ?? 0} ${AppLocalizations.of(context)!.seasonsLabel} · ${_episodes?.length ?? 0} ${AppLocalizations.of(context)!.episodes}',
                          ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _pill(String text) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.white12,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: Colors.white10),
      ),
      child: Text(
        text,
        style: const TextStyle(color: Colors.white, fontSize: 12),
      ),
    );
  }

  Widget _buildSeasonSelector(List seasons) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          AppLocalizations.of(context)!.seasonsLabel,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 16,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 10),
        SizedBox(
          height: 48,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            itemCount: seasons.length,
            itemBuilder: (context, index) {
              final season = seasons[index];
              final name = season is Map
                  ? (season['Name'] ?? '').toString()
                  : '';
              final isSelected = index == _selectedSeasonIndex;
              return Padding(
                padding: const EdgeInsets.only(right: 10),
                child: Focus(
                  autofocus: false,
                  onKeyEvent: (node, event) {
                    if (event is KeyDownEvent) {
                      final key = event.logicalKey;
                      if (key == LogicalKeyboardKey.select ||
                          key == LogicalKeyboardKey.accept ||
                          key == LogicalKeyboardKey.enter) {
                        setState(() {
                          _selectedSeasonIndex = index;
                        });
                        return KeyEventResult.handled;
                      }
                    }
                    return KeyEventResult.ignored;
                  },
                  child: Builder(
                    builder: (context) {
                      final node = Focus.of(context);
                      final isFocused = node.hasFocus;
                      final color = isSelected
                          ? Theme.of(context).colorScheme.primary
                          : (isFocused ? Colors.white24 : Colors.white12);
                      return GestureDetector(
                        onTap: () {
                          node.requestFocus();
                          setState(() {
                            _selectedSeasonIndex = index;
                          });
                        },
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 14,
                            vertical: 10,
                          ),
                          decoration: BoxDecoration(
                            color: color,
                            borderRadius: BorderRadius.circular(999),
                            border: Border.all(color: Colors.white10),
                          ),
                          child: Text(
                            name.isEmpty
                                ? '${AppLocalizations.of(context)!.season} ${index + 1}'
                                : name,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 13,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}
