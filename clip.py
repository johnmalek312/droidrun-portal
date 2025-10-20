#!/usr/bin/env python3
"""
Droidrun Portal Clipboard Manager
==================================

A robust clipboard manager for Droidrun Portal with full Unicode/emoji support.
Supports both ADB (ContentProvider) and HTTP (Socket Server) methods.

Usage:
    # Get clipboard
    python clip.py get

    # Set clipboard
    python clip.py set "Your text here"

    # Run tests
    python clip.py test

    # Use as a module
    from clip import get_clipboard, set_clipboard
    set_clipboard("Hello 🌍")
    text = get_clipboard()
"""

import subprocess
import json
import sys
import argparse
from typing import Optional, Literal


class DroidrunClipboard:
    """
    Robust clipboard manager for Droidrun Portal with full Unicode/emoji support.

    Supports both ADB (ContentProvider) and HTTP (Socket Server) methods.
    Automatically handles escaping, encoding, and all edge cases.
    """

    def __init__(self, method: Literal['auto', 'http', 'adb'] = 'auto', port: int = 8080):
        """
        Initialize clipboard manager.

        Args:
            method: 'auto' (try HTTP first, fallback to ADB), 'http', or 'adb'
            port: Port for HTTP socket server (default 8080)
        """
        self.method = method
        self.port = port
        self._http_available = None

    def _setup_port_forwarding(self) -> bool:
        """Setup ADB port forwarding for HTTP access."""
        result = subprocess.run(
            ['adb', 'forward', f'tcp:{self.port}', f'tcp:{self.port}'],
            capture_output=True,
            text=True
        )
        return result.returncode == 0

    def _is_http_available(self) -> bool:
        """Check if HTTP socket server is available."""
        if self._http_available is not None:
            return self._http_available

        try:
            import requests
            self._setup_port_forwarding()
            response = requests.get(f'http://localhost:{self.port}/ping', timeout=1)
            self._http_available = response.status_code == 200
        except:
            self._http_available = False

        return self._http_available

    def get(self) -> Optional[str]:
        """
        Get clipboard content with automatic method selection.

        Returns:
            Clipboard text content, or None if clipboard is empty
        """
        if self.method == 'http' or (self.method == 'auto' and self._is_http_available()):
            return self._get_http()
        else:
            return self._get_adb()

    def set(self, text: str) -> bool:
        """
        Set clipboard content with automatic method selection.
        Handles all Unicode characters, emojis, newlines, and special characters.

        Args:
            text: Text to set in clipboard (supports full Unicode)

        Returns:
            True if successful, False otherwise
        """
        if self.method == 'http' or (self.method == 'auto' and self._is_http_available()):
            return self._set_http(text)
        else:
            return self._set_adb(text)

    def _get_http(self) -> Optional[str]:
        """Get clipboard via HTTP socket server."""
        try:
            import requests
            response = requests.get(f'http://localhost:{self.port}/clipboard/get')
            response.raise_for_status()

            data = response.json()
            if data.get('status') == 'success':
                content = data.get('data', '')
                # Return empty string as-is, None only if truly no data
                return content
            return None
        except Exception as e:
            raise Exception(f"HTTP GET failed: {e}")

    def _set_http(self, text: str) -> bool:
        """
        Set clipboard via HTTP socket server.

        Note: Due to Android 10+ security restrictions, verification may fail even when
        set succeeds (clipboard access denied when app not in focus). We trust the
        server's response rather than reading back for verification.
        """
        try:
            import requests
            response = requests.post(
                f'http://localhost:{self.port}/clipboard/set',
                json={'text': text},
                headers={'Content-Type': 'application/json'}
            )
            response.raise_for_status()

            # Check server response (don't verify by reading back due to Android restrictions)
            return 'success' in response.text.lower()
        except Exception as e:
            raise Exception(f"HTTP SET failed: {e}")

    def _get_adb(self) -> Optional[str]:
        """Get clipboard via ADB ContentProvider."""
        try:
            result = subprocess.run(
                ['adb', 'shell', 'content', 'query', '--uri',
                 'content://com.droidrun.portal/clipboard/get'],
                capture_output=True,
                text=True
            )

            if result.returncode != 0:
                raise Exception(f"ADB command failed: {result.stderr}")

            for line in result.stdout.strip().split('\n'):
                if 'result=' in line:
                    json_str = line.split('result=', 1)[1]
                    outer_json = json.loads(json_str)

                    if outer_json.get('status') == 'success':
                        content = outer_json.get('data', '')
                        # Return empty string as-is, None only if truly no data
                        return content

            return None
        except Exception as e:
            raise Exception(f"ADB GET failed: {e}")

    def _set_adb(self, text: str) -> bool:
        """
        Set clipboard via ADB ContentProvider.
        Uses proper shell escaping to support all Unicode characters and special chars.

        Note: Due to Android 10+ security restrictions, verification may fail even when
        set succeeds (clipboard access denied when app not in focus). We trust the
        command succeeded if no error was returned.
        """
        try:
            # Escape backslashes first, then single quotes for shell
            # This handles paths like C:\Users\Test and text with quotes
            escaped_text = text.replace("\\", "\\\\").replace("'", "'\\''")

            # Build the ADB shell command with proper quoting
            cmd = f"content insert --uri content://com.droidrun.portal/clipboard/set --bind 'text:s:{escaped_text}'"

            result = subprocess.run(
                ['adb', 'shell', cmd],
                capture_output=True,
                text=True
            )

            # Success if command returned 0 (Android may block read verification)
            return result.returncode == 0

        except Exception as e:
            raise Exception(f"ADB SET failed: {e}")


# Global instance for convenience
_clipboard = DroidrunClipboard(method='auto')


# Convenience functions
def get_clipboard() -> Optional[str]:
    """
    Get current clipboard content (auto-selects best method).

    Returns:
        Clipboard text content, or None if empty
    """
    return _clipboard.get()


def set_clipboard(text: str) -> bool:
    """
    Set clipboard content (auto-selects best method, supports emojis/Unicode).

    Args:
        text: Text to set (supports full Unicode, emojis, special characters)

    Returns:
        True if successful, False otherwise
    """
    return _clipboard.set(text)


# Test functions
def run_basic_tests():
    """Run basic clipboard tests."""
    print("=" * 70)
    print("BASIC CLIPBOARD TESTS")
    print("=" * 70)
    print()

    tests_passed = 0
    tests_total = 0

    test_cases = [
        ("Simple text", "Hello, World!"),
        ("Emojis", "Hello 🌍! Testing emojis: 🚀 💻 ✨ 🎉"),
        ("Special chars", "Special: !@#$%^&*()_+-=[]{}|;':\",./<>?"),
        ("Multiline", "Line 1\nLine 2\nLine 3\tTabbed"),
        ("International", "你好 مرحبا Здравствуй こんにちは 안녕하세요"),
        ("Single quote", "It's working"),
        ("Double quotes", 'She said "Hello"'),
        ("Backslash", "Path: C:\\Users\\Test"),
        ("Mixed quotes", """It's a "test" with 'quotes'"""),
        ("Empty string", ""),
    ]

    for name, text in test_cases:
        tests_total += 1
        try:
            set_clipboard(text)
            retrieved = get_clipboard()
            passed = retrieved == text

            if passed:
                tests_passed += 1
                print(f"✓ {name:20} PASS")
            else:
                print(f"✗ {name:20} FAIL")
                print(f"  Expected: {repr(text)}")
                print(f"  Got:      {repr(retrieved)}")
        except Exception as e:
            print(f"✗ {name:20} ERROR: {e}")

    print()
    print("=" * 70)
    print(f"Results: {tests_passed}/{tests_total} tests passed")
    print("=" * 70)

    return tests_passed == tests_total


def run_stress_tests():
    """Run stress tests with edge cases."""
    print("\n" + "=" * 70)
    print("STRESS TESTS")
    print("=" * 70)
    print()

    tests_passed = 0
    tests_total = 0

    # Test 1: Large text
    tests_total += 1
    print("TEST 1: Large text (5000 characters)")
    try:
        large_text = "A" * 5000 + " END"
        set_clipboard(large_text)
        retrieved = get_clipboard()
        if retrieved == large_text:
            tests_passed += 1
            print(f"  ✓ PASS ({len(retrieved)} chars)")
        else:
            print(f"  ✗ FAIL (expected {len(large_text)}, got {len(retrieved)} chars)")
    except Exception as e:
        print(f"  ✗ ERROR: {e}")

    # Test 2: Rapid operations
    tests_total += 1
    print("\nTEST 2: Rapid set/get operations (10 iterations)")
    try:
        success_count = 0
        for i in range(10):
            text = f"Iteration {i}: 🚀"
            set_clipboard(text)
            if get_clipboard() == text:
                success_count += 1

        if success_count == 10:
            tests_passed += 1
            print(f"  ✓ PASS (10/10 iterations succeeded)")
        else:
            print(f"  ✗ FAIL ({success_count}/10 iterations succeeded)")
    except Exception as e:
        print(f"  ✗ ERROR: {e}")

    # Test 3: All printable ASCII
    tests_total += 1
    print("\nTEST 3: All printable ASCII characters")
    try:
        ascii_text = ''.join(chr(i) for i in range(32, 127))
        set_clipboard(ascii_text)
        retrieved = get_clipboard()
        if retrieved == ascii_text:
            tests_passed += 1
            print(f"  ✓ PASS (95 ASCII chars)")
        else:
            print(f"  ✗ FAIL")
    except Exception as e:
        print(f"  ✗ ERROR: {e}")

    print()
    print("=" * 70)
    print(f"Stress Tests: {tests_passed}/{tests_total} passed")
    print("=" * 70)

    return tests_passed == tests_total


def run_all_tests():
    """Run all clipboard tests."""
    basic_passed = run_basic_tests()
    stress_passed = run_stress_tests()

    print("\n" + "=" * 70)
    print("FINAL RESULTS")
    print("=" * 70)

    method = _clipboard.method
    if _clipboard._is_http_available():
        print(f"Method used: {method} (HTTP Socket Server)")
    else:
        print(f"Method used: {method} (ADB ContentProvider)")

    if basic_passed and stress_passed:
        print("\n🎉 ALL TESTS PASSED!")
        return 0
    else:
        print("\n⚠️  SOME TESTS FAILED")
        return 1


def main():
    """Command-line interface."""
    parser = argparse.ArgumentParser(
        description='Droidrun Portal Clipboard Manager',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s get                    # Get clipboard content
  %(prog)s set "Hello World"      # Set clipboard content
  %(prog)s set "Emoji test 🚀"   # Works with emojis
  %(prog)s test                   # Run all tests
  %(prog)s test --basic           # Run basic tests only
  %(prog)s test --stress          # Run stress tests only
        """
    )

    parser.add_argument(
        'action',
        choices=['get', 'set', 'test'],
        help='Action to perform'
    )

    parser.add_argument(
        'text',
        nargs='?',
        help='Text to set (required for "set" action)'
    )

    parser.add_argument(
        '--method',
        choices=['auto', 'http', 'adb'],
        default='auto',
        help='Connection method (default: auto)'
    )

    parser.add_argument(
        '--port',
        type=int,
        default=8080,
        help='HTTP socket server port (default: 8080)'
    )

    parser.add_argument(
        '--basic',
        action='store_true',
        help='Run basic tests only'
    )

    parser.add_argument(
        '--stress',
        action='store_true',
        help='Run stress tests only'
    )

    args = parser.parse_args()

    # Update global clipboard instance
    global _clipboard
    _clipboard = DroidrunClipboard(method=args.method, port=args.port)

    try:
        if args.action == 'get':
            text = get_clipboard()
            if text is not None:
                print(text)
                return 0
            else:
                print("(Clipboard is empty)", file=sys.stderr)
                return 1

        elif args.action == 'set':
            if args.text is None:
                print("Error: Text argument required for 'set' action", file=sys.stderr)
                parser.print_help()
                return 1

            success = set_clipboard(args.text)
            if success:
                print(f"✓ Clipboard set successfully ({len(args.text)} chars)")
                return 0
            else:
                print("✗ Failed to set clipboard", file=sys.stderr)
                return 1

        elif args.action == 'test':
            if args.basic:
                return 0 if run_basic_tests() else 1
            elif args.stress:
                return 0 if run_stress_tests() else 1
            else:
                return run_all_tests()

    except KeyboardInterrupt:
        print("\n\nInterrupted by user", file=sys.stderr)
        return 130
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
