# Microbot adapter patches

The production shadow adapter requires a deliberately small delta from the pinned core:

- retain the exact selected `Transport` on each `PathStep` (the same patch used by the comparison harness);
- allow an immutable edge override for Microbot's pinned live-collision snapshot;
- allow an immutable walking-cost policy for dangerous-tile penalties;
- expose the transport-availability builder to the package-external adapter;
- replace the upstream plugin class with a resource/config compatibility anchor only.

None of these hooks owns execution, reads Microbot globals, or changes queue ordering when its supplied
policy returns the default value.
