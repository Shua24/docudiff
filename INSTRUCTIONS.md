# Docudiff - Plan

Here's a plan for Docudiff, a document diff viewer and version manager,
written in Java.

## Features (Functional Requirements)

Here are the functional requirements:

- The user shall be able to view the diff of a document
- The user shall be able to branch document versions like Git does
- The user shall be able to view the current document like Microsoft word does.

(Basically, making Git-like functionalities for a document),
grill me on that if it's hard to still understand.

## Accepted documents

Here are the acceptable documents

- Word DOCX files
- XLSX files
- Markdown files (with Git style view)
- GDOC Google Docs files (if possible) - Still planned

## Deployment

The deployemnt of the app shall be for native systems instead of the web.
It shall be native-first, as building a cloud infrastructure just for that
is a nightmare.

## Project Assumptions

Here are the assumptions that will explain the constraints indirectly.

- The users barely know the CLI.
- The users are geared towards a GUI-centric UX.
- The users are more familiar with document platforms such as Microsoft Word and
other document processing apps and platforms.

## Implementation constraints

Here are the implementation constraints of the entire project

- The project shall be offline-first.
- The project shall be GUI-centric.

## Technical Requirements

Here are the technical requirements.

- The project shall be written in Java.
- The project shall use JGit as the version management engine.
- Other dependencies shall have the "ask-first" approach.
