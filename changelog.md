# Added

* When an invalid script configuration is found (ie. when the script is updated and a new configuration value is
  required) a warning icon will appear at the script.
* To view the script configuration, hover over a script and click on the "Show more" icon, then select "View
  configuration"
* Complex list configuration items can be filled in by importing a CSV. Download a template with the expected column
  headers, fill it in with your spreadsheet, and the rows appear as ordinary editable rows. Up to 5000 rows per file.

# Changed

* CSV imports now accept semicolon- and tab-separated files; the separator is detected from the header row.
* CSV imports are stricter about yes/no and dropdown columns. A yes/no column must hold `true`/`false`, `yes`/`no` or
  `1`/`0` (any capitalisation) — anything else is now rejected instead of being read as "no". A dropdown column must
  match one of the script's values exactly — anything else is now rejected instead of being left empty. Existing
  custom dataset files that relied on the old behaviour need their values corrected.

# Fixed

* A yes/no field inside a complex list kept its value when reopening a configuration or copying an existing script,
  instead of reverting to unset.
