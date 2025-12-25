import 'package:emby_tv/app_model.dart';
import 'package:emby_tv/pages/media_detail_screen.dart';
import 'package:emby_tv/widgets/build_gradient_background.dart';
import 'package:emby_tv/widgets/build_item.dart';
import 'package:emby_tv/widgets/loading.dart';
import 'package:emby_tv/widgets/no_data.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

class LibraryScreen extends StatefulWidget {
  final String libraryId;
  final String libraryName;
  final String type;
  const LibraryScreen({
    required this.libraryId,
    required this.libraryName,
    required this.type,
    super.key,
  });

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> {
  List? _libraryList;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _getData();
    });
  }

  void _getData() async {
    final model = context.read<AppModel>();
    List? list = await model.getLibraryList(widget.libraryId, widget.type);

    setState(() {
      _libraryList = list;
    });
  }

  void onSelected(Map item) {
    Navigator.of(context)
        .push(
          MaterialPageRoute(
            builder: (context) =>
                MediaDetailScreen(mediaId: item['Id'].toString()),
          ),
        )
        .then((_) => _getData());
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: BuildGradientBackground(
        child: _libraryList == null
            ? Loading()
            : _libraryList!.isEmpty
            ? NoData()
            : Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 32.0,
                      vertical: 16.0,
                    ),
                    child: Text(
                      widget.libraryName,
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
                  Expanded(
                    child: GridView.builder(
                      itemCount: _libraryList!.length,
                      padding: const EdgeInsets.all(20),
                      gridDelegate:
                          const SliverGridDelegateWithMaxCrossAxisExtent(
                            maxCrossAxisExtent: 160,
                            crossAxisSpacing: 20,
                            mainAxisSpacing: 20,
                            childAspectRatio: 0.55,
                          ),
                      itemBuilder: (context, index) {
                        Map item = _libraryList![index];
                        return Focus(
                          autofocus: index == 0,
                          onKeyEvent: (node, event) {
                            if (event is KeyDownEvent) {
                              if (event.logicalKey ==
                                      LogicalKeyboardKey.accept ||
                                  event.logicalKey ==
                                      LogicalKeyboardKey.select ||
                                  event.logicalKey ==
                                      LogicalKeyboardKey.enter) {
                                onSelected(item);
                                return KeyEventResult.handled;
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
                                  onSelected(item);
                                },
                                child: BuildItem(
                                  item: item,
                                  width: 120,
                                  isContinueWatching: false,
                                  isMyLibrary: false,
                                  isFocused: isFocused,
                                  imageBoxFit:
                                      (item["PrimaryImageAspectRatio"] ?? 1) <=
                                          1
                                      ? BoxFit.fill
                                      : BoxFit.fitWidth,
                                ),
                              );
                            },
                          ),
                        );
                      },
                    ),
                  ),
                ],
              ),
      ),
    );
  }
}
