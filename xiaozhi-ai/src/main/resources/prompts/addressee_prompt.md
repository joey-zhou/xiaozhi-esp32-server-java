The voice assistant is speaking out loud right now. Its microphone picked up the utterance below. Decide whether that utterance was directed at the assistant.

Recent conversation:
$history$

What the assistant is saying right now:
$spoken$

The utterance that interrupted it:
$utterance$

Answer true when the utterance is directed at the assistant: a command, a question, a request, a correction, an answer to what the assistant just asked, or anything else that continues the conversation with it.

Answer false only when the utterance is clearly not directed at the assistant: the user talking to another person in the room, someone else's conversation, a television or radio, or speech read aloud to a third party.

When you are not sure, answer true. Ignoring a real command is far worse than replying when nobody asked.

Reply with JSON and nothing else, in exactly this shape:
{"directed": true}
