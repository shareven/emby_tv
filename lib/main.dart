// main.dart
import 'package:emby_tv/app_model.dart';
import 'package:emby_tv/pages/home_screen.dart';
import 'package:emby_tv/pages/login_screen.dart';
import 'package:emby_tv/widgets/loading.dart';
import 'package:flutter/material.dart';
import 'package:overlay_support/overlay_support.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'l10n/app_localizations.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  runApp(
    MultiProvider(
      providers: [ChangeNotifierProvider(create: (_) => AppModel(prefs))],
      child: const MyApp(),
    ),
  );
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    Color themeColor = Colors.pink;
    return OverlaySupport.global(
      child: MaterialApp(
        title: 'Emby TV',
        localizationsDelegates: const [
          AppLocalizations.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: const [Locale('en'), Locale('zh')],
        localeResolutionCallback: (locale, supportedLocales) {
          if (locale == null) return supportedLocales.first;
          for (final supported in supportedLocales) {
            if (supported.languageCode == locale.languageCode) return supported;
          }
          return supportedLocales.first;
        },
        themeMode: ThemeMode.system,
        // darkTheme: ThemeData(
        //   brightness: Brightness.dark,
        //   cardTheme: const CardTheme(
        //       color: Color.fromARGB(221, 28, 28, 28),
        //       elevation: 0,
        //       margin: EdgeInsets.zero),
        //   colorScheme: ColorScheme.dark(
        //     surface: Color.fromARGB(248, 17, 17, 17),
        //     primary: themeColor,
        //     onPrimary: Colors.white,
        //     secondary: Colors.blueAccent,
        //   ),
        // ),
        theme: ThemeData(
          cardTheme: const CardThemeData(
            color: Colors.white,
            elevation: 0,
            margin: EdgeInsets.zero,
          ),
          tabBarTheme: const TabBarThemeData(
            labelColor: Colors.white,
            indicatorColor: Colors.white,
            unselectedLabelColor: Colors.white54,
          ),
          appBarTheme: AppBarTheme(
            foregroundColor: Colors.white,
            backgroundColor: themeColor,
          ),
          colorScheme: ColorScheme.fromSeed(
            seedColor: themeColor,
            primary: themeColor,
            secondary: Colors.black87,
          ),
          secondaryHeaderColor: themeColor.withAlpha(35),
          useMaterial3: true,
        ),
        home: Consumer<AppModel>(
          builder: (context, model, _) {
            if (!model.isLoaded) {
              return const Loading();
            }
            if (model.isLoggedIn) {
              return const HomeScreen();
            } else {
              return const LoginScreen();
            }
          },
        ),
      ),
    );
  }
}
