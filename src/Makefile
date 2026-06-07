# This must build both plugin and standalone app(s).
# would like to avoid having duplicates of classes
# in ../sim, but standalone JAR needs them copied.
# Perhaps 'jdeps' can help with this.

JSERIAL = jserialcomm/jSerialComm-2.9.3.jar

# For the plugin, these must come from the parent
SIM_CLASS = GenericHelp.class \
	SimResource.class \
	ExtBoolean.class \
	ParityGenerator.class

CORE_CLASS = ASR33.class \
	ASR33Reader.class \
	BlockCaret.class \
	PaperTapePositioner.class \
	TermContainer.class \
	RdrContainer.class \
	SuffFileChooser.class \
	SuffFileFilter.class \
	Bell.class \
	Paster.class \
	Typer.class

SUB_CLASS = 'PaperTapePositioner$$PaperTape.class'

JARS = ASR33telnet.jar ASR33tty.jar ASR33plugin.jar

AUXS = $(wildcard doc/*) $(wildcard fonts/*.ttf) $(wildcard *.wav)

all: $(CORE_CLASS) $(SIM_CLASS)

%.class: %.java
	javac $?

ASR33tty.class: ASR33tty.java $(JSERIAL)
	javac -cp .:$(JSERIAL) $<

jar: $(JARS)

ASR33telnet.jar: ASR33telnet.class $(CORE_CLASS) $(SIM_CLASS) $(AUXS)
	echo "Main-Class: ASR33telnet" >Manifest.txt
	jar -cmf Manifest.txt $@ $^ $(SUB_CLASS)

ASR33tty.jar: ASR33tty.class $(CORE_CLASS) $(SIM_CLASS) $(AUXS) $(JSERIAL)
	echo "Main-Class: ASR33tty" >Manifest.txt
	jar -cmf Manifest.txt $@ $^ $(SUB_CLASS)

# Need to exclude ASR33telnet.class...
ASR33plugin.jar: $(CORE_CLASS) $(AUXS)
	jar -cMf $@ $^

ship: jar
	cp $(JARS) ..

__FRC__:
