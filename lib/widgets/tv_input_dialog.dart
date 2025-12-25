import 'package:flutter/material.dart';

class TvInputDialog extends StatefulWidget {
  final TextEditingController controller;
  final String label;
  final bool isPassword;

  const TvInputDialog({
    required this.controller,
    required this.label,
    this.isPassword = false,
    super.key,
  });

  @override
  State<TvInputDialog> createState() => _TvInputDialogState();
}

class _TvInputDialogState extends State<TvInputDialog> {
  late TextEditingController _textController;

  @override
  void initState() {
    super.initState();
    _textController = widget.controller;
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.label),
      content: SizedBox(
        width: 600,
        child: TextField(
          controller: _textController,
          obscureText: widget.isPassword,
          autofocus: true,
          style: const TextStyle(fontSize: 24),
          onSubmitted: (e) => Navigator.pop(context),
          decoration: InputDecoration(
            border: OutlineInputBorder(),
            suffixIcon: IconButton(
              icon: const Icon(Icons.close),
              onPressed: () => Navigator.pop(context),
            ),
          ),
        ),
      ),
    );
  }
}
