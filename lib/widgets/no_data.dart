import 'package:flutter/material.dart';

class NoData extends StatelessWidget {
  const NoData({super.key});

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Text(
        "No content",
        style: TextStyle(color: Colors.grey),
      ),
    );
  }
}
