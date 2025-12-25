import 'package:emby_tv/app_model.dart';
import 'package:emby_tv/pages/library_screen.dart';
import 'package:emby_tv/pages/player_screen.dart';
import 'package:emby_tv/pages/update_screen.dart';
import 'package:emby_tv/widgets/build_gradient_background.dart';
import 'package:emby_tv/widgets/build_item.dart';
import 'package:emby_tv/widgets/no_data.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:emby_tv/l10n/app_localizations.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadVersion();
    });
  }

  Future<void> _loadVersion() async {
    if (mounted) {
      await context.read<AppModel>().checkUpdate();
    }
  }

  Widget _buildSection(
    BuildContext context,
    String title,
    List<dynamic> items,
    int sectionIndex,
  ) {
    final isMyLibrary = sectionIndex == 0;
    final isContinueWatching = sectionIndex == 1;
    double height = isMyLibrary
        ? 160
        : isContinueWatching
        ? 200
        : 220;
    List aspectRatios = items
        .map((e) => (e["PrimaryImageAspectRatio"] ?? 1).toDouble())
        .toList();
    double maxAspectRatio = aspectRatios.reduce(
      (currMax, ratio) => ratio > currMax ? ratio : currMax,
    );
    double width = maxAspectRatio * height - (maxAspectRatio < 1 ? 30 : 90);
    if (isMyLibrary) {
      width = 230;
    }
    bool needUpdate = context.read<AppModel>().needUpdate;
    return Column(
      key: Key(sectionIndex.toString()),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 32.0, vertical: 16.0),
          child: Text(
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
        ),
        SizedBox(
          height: height,
          child: items.isEmpty
              ? NoData()
              : ListView.builder(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 24.0),
                  itemCount: items.length,
                  itemBuilder: (context, index) {
                    return _buildMediaItem(
                      item: items[index],
                      width: width,
                      sectionIndex: sectionIndex,
                      itemIndex: index,
                      isContinueWatching: isContinueWatching,
                      isMyLibrary: isMyLibrary,
                      needUpdate: needUpdate,
                    );
                  },
                ),
        ),
        const SizedBox(height: 24),
      ],
    );
  }

  Widget _buildMediaItem({
    required Map item,
    required double width,
    required int sectionIndex,
    required int itemIndex,
    required bool isContinueWatching,
    required bool isMyLibrary,
    required bool needUpdate,
  }) {
    
    return Padding(
      key: Key(itemIndex.toString()),
      padding: const EdgeInsets.symmetric(horizontal: 8.0),
      child: Focus(
        autofocus: isContinueWatching && itemIndex == 0,
        onKeyEvent: (node, event) {
          if (event is KeyDownEvent) {
            final key = event.logicalKey;
            if (key == LogicalKeyboardKey.accept ||
                key == LogicalKeyboardKey.select ||
                key == LogicalKeyboardKey.enter) {
              onSelected(item, isMyLibrary);
              return KeyEventResult.handled;
            }
            if (key == LogicalKeyboardKey.contextMenu ||
                key == LogicalKeyboardKey.browserFavorites) {
              _showMenuDialog(needUpdate);
            }
          }
          return KeyEventResult.ignored;
        },
        child: Builder(
          builder: (context) {
            FocusNode node = Focus.of(context);
            bool isFocused = node.hasFocus;
            return GestureDetector(
              onTap: () {
                node.requestFocus();
                onSelected(item, isMyLibrary);
              },
              child: BuildItem(
                item: item,
                width: width,
                isContinueWatching: isContinueWatching,
                isMyLibrary: isMyLibrary,
                isFocused: isFocused,
              ),
            );
          },
        ),
      ),
    );
  }

  void _showMenuDialog(bool needUpdate) async {
    
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
                      _logout();
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
                        AppLocalizations.of(context)!.logout,
                        style: TextStyle(color: Colors.white, fontSize: 24),
                      ),
                      tileColor: isFocused
                          ? Theme.of(context).colorScheme.primary
                          : null,
                    );
                  },
                ),
              ),
              if (needUpdate)
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

  void _logout() {
    final model = context.read<AppModel>();
    model.logout();
  }

  @override
  Widget build(BuildContext context) {
    final model = context.watch<AppModel>();
    final List<Widget> sections = [
      SizedBox(height: 10),
      _buildSection(
        context,
        AppLocalizations.of(context)!.myLibraries,
        model.libraryLatestItems,
        0,
      ),
      _buildSection(
        context,
        AppLocalizations.of(context)!.continueWatching,
        model.resumeItems,
        1,
      ),
      SizedBox(height: 10),
    ];

    sections.addAll(
      model.libraryLatestItems.asMap().entries.map(
        (e) => _buildSection(
          context,
          e.value['Name'],
          e.value['latestItems'],
          e.key + 2,
        ),
      ),
    );

    String newVerson = model.newVersion;
    String versionUpdateNote = '';
    if (model.needUpdate) {
      versionUpdateNote = AppLocalizations.of(
        context,
      )!.newVersionAvailable(newVerson);
    }
    return Scaffold(
      body: BuildGradientBackground(
        child: SafeArea(
          child: Column(
            children: [
              Container(
                padding: const EdgeInsets.all(3),
                color: Colors.black.withAlpha(70),
                child: Row(
                  children: [
                    Icon(Icons.menu, size: 10, color: Colors.white),
                    const SizedBox(width: 2),
                    Text(
                      "${AppLocalizations.of(context)!.pressMenuToShowMenu}  ${model.currentVersion}  $versionUpdateNote",
                      style: const TextStyle(
                        fontSize: 10,
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
                    const SizedBox(width: 32),
                  ],
                ),
              ),
              SizedBox(height: 5),
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.only(bottom: 40),
                  children: sections,
                ),
              ),
              SizedBox(height: 5),
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

  void _playCurrentItem(Map item) {
    final model = context.read<AppModel>();

    Navigator.of(context)
        .push(
          MaterialPageRoute(
            builder: (context) => PlayerScreen(
              mediaId: item['Id'],
              isSeries: item['Type'] == "Series",
              playbackPositionTicks: item['UserData']?['PlaybackPositionTicks'],
            ),
          ),
        )
        .then((_) => model.loadData());
  }

  void _goLibraryScreen(Map item) {
    final model = context.read<AppModel>();
    Navigator.of(context)
        .push(
          MaterialPageRoute(
            builder: (context) => LibraryScreen(
              libraryName: item['Name'],
              libraryId: item['Id'],
              type: item['latestItems']?[0]?['Type'] ?? '',
            ),
          ),
        )
        .then((_) => model.loadData());
  }

  void onSelected(Map item, bool isMyLibrary) {
    if (isMyLibrary) {
      _goLibraryScreen(item);
    } else {
      _playCurrentItem(item);
    }
  }
}
