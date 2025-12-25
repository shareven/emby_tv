
import 'package:emby_tv/app_model.dart';
import 'package:emby_tv/utils.dart';
import 'package:emby_tv/widgets/build_gradient_background.dart';
import 'package:flutter/material.dart';
import 'package:ota_update_fork/ota_update_fork.dart';
import 'package:provider/provider.dart';

class UpdateScreen extends StatefulWidget {
  const UpdateScreen({super.key});

  @override
  State<UpdateScreen> createState() => _UpdateScreenState();
}

class _UpdateScreenState extends State<UpdateScreen> {
  OtaEvent? currentEvent;

  Future<void> tryOtaUpdate() async {
    String downloadUrl = context.read<AppModel>().downloadUrl;
    try {

      OtaUpdate()
          .execute(
            downloadUrl,
            destinationFilename:"emby_tv.apk",
          )
          .listen((OtaEvent event) {
            if (!mounted) return;
            setState(() => currentEvent = event);
          });
    } catch (e) {
      showErrorMsg('Update failed! Details: $e');
    }
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((e) {
      tryOtaUpdate();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: BuildGradientBackground(
        child: Center(
          child:
              currentEvent != null
                  ? Container(
                    height: 250,
                    padding: const EdgeInsets.all(15),
                    child: Column(
                      children: <Widget>[
                        double.tryParse(currentEvent!.value ?? '') is double
                            ? LinearProgressIndicator(
                              backgroundColor: Theme.of(context).disabledColor,
                              semanticsLabel: currentEvent!.value,
                              value: double.parse(currentEvent!.value!) / 100,
                              semanticsValue: currentEvent!.value,
                            )
                            : Container(),
                        const SizedBox(height: 25),
                        Container(
                          margin: const EdgeInsets.only(bottom: 50),
                          child: Text(
                            '${currentEvent!.status == OtaStatus.DOWNLOADING ? 'Downloading' : (currentEvent!.status == OtaStatus.INSTALLING ? 'Installing...' : '')} ${(currentEvent!.status == OtaStatus.DOWNLOADING ? ':' : '')} ${currentEvent!.value}${currentEvent!.status == OtaStatus.DOWNLOADING ? '%' : ''} \n',
                            style: const TextStyle(fontSize: 25),
                          ),
                        ),
                      ],
                    ),
                  )
                  : const CircularProgressIndicator(),
        ),
      ),
    );
  }
}
