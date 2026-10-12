# Template media seeds

Place project-owned seed assets under `media/` when the selected screens need
them. The shared bootstrap creates the configured bucket even when that
directory is absent. It copies only these seed assets and never removes uploads.

Do not place placeholder files in `media/`: they would become stored objects.
