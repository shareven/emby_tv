import 'package:cached_network_image/cached_network_image.dart';
import 'package:emby_tv/app_model.dart';
import 'package:emby_tv/utils.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class BuildItem extends StatelessWidget {
  final Map item;
  final double width;
  final bool isContinueWatching;
  final bool isMyLibrary;
  final bool isFocused;
  final bool isShowOverview;
  final BoxFit imageBoxFit;

  const BuildItem({
    required this.item,
    required this.width,
    required this.isContinueWatching,
    required this.isMyLibrary,
    required this.isFocused,
    this.isShowOverview = false,
    this.imageBoxFit = BoxFit.fitWidth,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    Color primaryColor = Theme.of(context).primaryColor;
    bool isSeries = item['Type'] == "Series";
    bool isPlayed = item['UserData']['Played'];
    String serverUrl = context.read<AppModel>().serverUrl!;
    return AnimatedContainer(
      duration: const Duration(milliseconds: 100),
      curve: Curves.easeInOut,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        gradient: isFocused
            ? const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Colors.pink, Colors.pinkAccent],
              )
            : null,
      ),
      child: Card(
        elevation: 3,
        color: Colors.transparent,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: SizedBox(
            width: width,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      Container(
                        color: Colors.grey[800],
                        child: CachedNetworkImage(
                          imageUrl: getImageUrl(
                            serverUrl,
                            item,
                            isContinueWatching,
                          ),
                          fit: imageBoxFit,
                          progressIndicatorBuilder: (context, url, progress) =>
                              Container(
                                color: Colors.grey[800],
                                child: Center(
                                  child: CircularProgressIndicator(
                                    value: progress.progress,
                                    color: Theme.of(context).primaryColor,
                                  ),
                                ),
                              ),
                          errorWidget: (context, url, error) => Container(
                            color: Colors.grey[800],
                            child: const Icon(Icons.error, color: Colors.white),
                          ),
                        ),
                      ),
                      if (isFocused)
                        Container(
                          decoration: BoxDecoration(
                            gradient: LinearGradient(
                              begin: Alignment.topCenter,
                              end: Alignment.bottomCenter,
                              colors: [
                                Colors.transparent,
                                primaryColor.withValues(alpha: 0.3),
                              ],
                              stops: const [0.6, 1.0],
                            ),
                          ),
                        ),
                      if (isSeries)
                        Positioned(
                          top: 10,
                          right: 10,
                          child: Badge(
                            textColor: Colors.white,
                            backgroundColor: primaryColor,
                            largeSize: 20,
                            label: Text(
                              item['UserData']['UnplayedItemCount'].toString(),
                            ),
                          ),
                        ),
                      if (isPlayed)
                        Positioned(
                          top: 10,
                          right: 10,
                          child: CircleAvatar(
                            backgroundColor: primaryColor,
                            radius: 10,
                            child: Icon(
                              Icons.check,
                              color: Colors.white,
                              size: 15,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
                if (!isSeries && !isMyLibrary)
                  SizedBox(
                    height: 3,
                    child: ClipRRect(
                      child: LinearProgressIndicator(
                        minHeight: 3,
                        value:
                            (item['UserData']['PlaybackPositionTicks'] ?? 0) /
                            (item['RunTimeTicks'] ?? 1),
                        backgroundColor: Colors.grey[800],
                        valueColor: AlwaysStoppedAnimation<Color>(
                          Colors.white70,
                        ),
                      ),
                    ),
                  ),
                Padding(
                  padding: const EdgeInsets.all(6.0),
                  child: Center(
                    child: SizedBox(
                      height: isShowOverview ? 92 : null,
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          Text(
                            isShowOverview
                                ? item['Name'] ?? 'Unknown title'
                                : (item['SeriesName'] ??
                                      item['Name'] ??
                                      'Unknown title'),
                            style: TextStyle(
                              fontSize: 13,
                              color: Colors.white,
                              fontWeight: FontWeight.bold,
                            ),
                            maxLines: 1,
                            textAlign: TextAlign.center,
                            overflow: TextOverflow.fade,
                          ),
                          if (!isMyLibrary)
                            Text(
                              item['ParentIndexNumber'] != null
                                  ? "S${item['ParentIndexNumber']}:E${item['IndexNumber']} ${item['Name']}"
                                  : "${item['ProductionYear'] ?? '--'}",
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.grey[300],
                                fontWeight: FontWeight.bold,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.fade,
                            ),
                          if (isShowOverview)
                            Text(
                              item['Overview'] ?? '',
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.grey[400],
                              ),
                              maxLines: 3,
                              overflow: TextOverflow.ellipsis,
                            ),
                        ],
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
