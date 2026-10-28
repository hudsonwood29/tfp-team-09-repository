# Airline Reliability - Team Starter

This is a starter, not a completed final project. TFP1 imports BTS records and
prints historical overall and per-operating-airline reliability statistics.
TFP2 is a placeholder for the final Vaadin application.

## Add to your existing team repository

Extract this ZIP outside your repository first. Copy TFP1 and TFP2 into the
repository shown by GitHub Desktop > Repository > Show in Explorer.
Do not replace existing teammate work: merge files if either folder exists.
Merge the included .gitignore rules into your existing .gitignore.
Keep your existing README content and add these setup instructions to it.

## Data setup (each teammate does this locally)

Source: https://transtats.bts.gov/DL_SelectFields.aspx?QO_fu146_anzr=b0-gvzr&gnoyr_VQ=FGJ

Use the verified January 2026 BTS download shared by the team in Slack.
Extract T_ONTIME_REPORTING.csv into TFP1/data/. Create data if it is missing.
Never commit the CSV or ZIP; the assignment requires sharing data separately.
Pin the download/file in Slack and document any later dataset changes.

## Run in IntelliJ (JDK 17 or newer, including your JDK 21)

Open TFP1/build.gradle as a project and let Gradle sync. Open Main.java and run
Main.main(). Set the run configuration's working directory to the TFP1 folder.
For January 2026, expect 544003 total records and 25635 cancelled records.

## Run without Gradle on Windows

With a JDK installed and java/javac on PATH, open Command Prompt inside TFP1:

    run-windows.bat

This compiles only the initial dependency-free importer. It is not a replacement
for Gradle when later features use Tablesaw/SMILE. No complete Gradle wrapper was
provided (the wrapper JAR/properties were missing), so this package does not ship
nonfunctional gradlew scripts. You can preserve your existing complete wrapper.
With a compatible Gradle installation, you can also run `gradle run` from TFP1.

## Definitions and limitations

- Cancellation rate uses every imported flight as its denominator.
- Arrival statistics use noncancelled, nondiverted records with an arrival delay.
- Late arrival means 15 minutes or more; early arrival remains negative in the mean.
- Empty delay fields are not replaced with zero.
- Operating airline codes are not necessarily the marketed brand (regional partners).
- January alone does not support year-round seasonal conclusions.
- No Ignav integration, trained model, optimizer, or website is implemented yet.

## Collaboration and next steps

Pull before working, use separate branches, review pull requests, and commit often.
Hudson: analysis and model evaluation; partner 1: Ignav and itinerary objects;
partner 2: Vaadin UI. Agree on shared class interfaces before integrating.
Add chronological train/test separation and avoid using actual delay/cancellation
outcomes as predictor inputs. Assess probabilities, not just classification accuracy.

Never commit API keys. Configure them through local environment variables.

## AI assistance disclosure

ChatGPT generated this initial project structure, importer, statistics class,
launch script, and setup notes, and helped explain missing values/denominators.
Review and understand the code before submission. Extend this disclosure as work
continues; it is not a substitute for the final mapped citations document.
