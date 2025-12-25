import 'package:flutter/material.dart';

class BuildGradientBackground extends StatefulWidget {
  final Widget child;
  const BuildGradientBackground({required this.child, super.key});

  @override
  State<BuildGradientBackground> createState() =>
      _BuildGradientBackgroundState();
}

class _BuildGradientBackgroundState extends State<BuildGradientBackground>
    with SingleTickerProviderStateMixin {
  late AnimationController _gradientController;

  @override
  void initState() {
    super.initState();
    _gradientController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 5),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    _gradientController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _gradientController,
      builder: (ctx, w) {
        return Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                Colors.blueAccent
                    .withValues(alpha: 0.5 + 0.2 * _gradientController.value),
                Colors.purpleAccent.withValues(
                    alpha: 0.5 + 0.2 * (1 - _gradientController.value)),
              ],
              stops: const [0.3, 0.7],
            ),
          ),
          child: widget.child,
        );
      },
    );
  }
}
