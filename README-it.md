# <img src="https://codeberg.org/monocles/monocles_chat/raw/branch/master/src/monocleschat/ic_launcher-playstore.png" width="30"> monocles chat

🇩🇪… [Versione tedesca del Readme disponibile qui](README.md) / 🇬🇧🇺🇸… [English Readme version available here](README-en.md) / [Francais ici](README-fr.md) / [Przeczytaj po polsku](README-pl.md)

monocles chat è un client XMPP Android moderno e sicuro. Basato su blabber.im e [Conversations](https://github.com/siacs/Conversations) ma con molte modifiche e funzionalità aggiuntive.
Le modifiche mirano a migliorare l'usabilità e facilitare la transizione da chat preinstallate e altre chat diffuse. Ecco alcune schermate:

<img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/00.png" width="200" /> <img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/01.png" width="200" /> <img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/02.png" width="200" /> <img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/03.png" width="200" /> <img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/04.png" width="200" /> <img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/05.png" width="200" /> <img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/06.png" width="200" /> <img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/07.png" width="200" />

Schermate progettate da Pigeonalley (https://pigeonalley.com)

### Impostazioni predefinite

monocles chat ha impostazioni predefinite diverse rispetto a blabber.im:

* non mostra anteprime di link web nella chat
* non mostra anteprime di posizioni nella chat
* non scarica automaticamente tutti gli allegati

### OTR

monocles chat supporta la crittografia OTR! Anche se non è facile da usare, OTR ha alcuni vantaggi:
<a href="https://en.wikipedia.org/wiki/Off-the-Record_Messaging#Implementation">https://en.wikipedia.org/wiki/Off-the-Record_Messaging#Implementation</a>

## Download
monocles chat è disponibile per l'installazione su F-Droid
In alternativa, gli APK delle versioni beta sono disponibili tramite codeberg: [Releases](https://codeberg.org/Arne/monocles_chat/releases) 

<a href="https://f-droid.org/app/de.monocles.chat"><img src="https://f-droid.org/badge/get-it-on-it.png" alt="Scaricalo su F-Droid" height="100"></a>
<a href="https://play.google.com/store/apps/details?id=eu.monocles.chat"><img src="https://codeberg.org/monocles/monocles_chat/raw/branch/master/art/GetItOnGooglePlay_Badge_Web_color_Italian.png" alt="Now on Google Play" height="100"></a>




#### monocles chat nightly e beta

Gli APK delle versioni nightly o beta sono disponibili tramite codeberg: [Releases](https://codeberg.org/Arne/monocles_chat/releases) 

## Social Media
Seguici su <a rel="me" href="https://monocles.social/@monocles">monocles social</a>

Ci sono anche stanze XMPP in inglese e tedesco incentrate sul supporto e lo sviluppo di monocles chat.

Se sei interessato allo sviluppo della chat, ecco una MUC per te (in inglese e tedesco):

Chat di sviluppo: [development@conference.monocles.de](https://monocles.chat/)     


C'è anche una MUC di supporto dove puoi fare domande e ottenere aiuto con i problemi che potresti incontrare, vedi sotto per i dettagli.


## Come posso aiutare con le traduzioni?
Puoi creare una merge request e aggiungere nuove lingue come locale e aggiungere e modificare traduzioni già esistenti.



## Aiuto! Ho riscontrato problemi!
Il modo più semplice per ottenere aiuto è unirsi alla nostra MUC di supporto (sia in inglese che in tedesco).  

Link di invito alla chat di supporto: [support@conference.monocles.eu](https://monocles.chat/)     

Se non riusciamo a risolvere il tuo problema lì, puoi aprire un issue [qui](https://codeberg.org/Arne/monocles_chat/issues), descrivendo in dettaglio il tuo problema, come riprodurlo e fornire i log. Vedi le istruzioni sotto su come creare file di log.



### Come creare log di debug? (adb)

#### GNU/Linux, OSX e altri sistemi Unix-like:

1. Prima installa **A**ndroid **D**ebugging **B**ridge, se non già presente.
    ###### Debian e derivati come Ubuntu / Linux Mint
    ```
    sudo apt-get update
    sudo apt-get install adb
    # Per Debian Jessie e precedenti:
    # sudo apt-get install android-tools-adb
    ```
    ###### openSUSE 42.2 e 42.3
    ```
    sudo zypper ref
    sudo zypper install android-tools
    ```
    ###### openSUSE Tumbleweed
    qui devi aggiungere il seguente repository (ad esempio tramite Yast):
    http://download.opensuse.org/repositories/hardware/openSUSE_Tumbleweed/
    
    in alternativa hai l'opzione di usare il `1 Click installer` 
    https://software.opensuse.org/package/android-tools
    ###### altri sistemi
    installa adb usando un metodo appropriato per il tuo sistema 
    
2. Ora apri un terminale in una directory di tua scelta, o naviga nella directory usando `cd`.

3. Segui i passaggi da [6] a [10] delle istruzioni Windows.

4. Inizia a salvare il tuo log in un file sul tuo computer. Useremo `logcat.txt`. Inserisci:
    ```
    $ adb -d logcat -v time | grep -i "monocles chat" > logcat.txt
    ```

5. Segui i rimanenti passaggi [12] e [13] delle istruzioni Windows.


#### Windows:

1. Scarica gli strumenti della piattaforma SDK di Google per il tuo sistema operativo:
    
    https://developer.android.com/studio/releases/platform-tools.html    
2. Nel caso non fossero inclusi: Hai anche bisogno dei driver ADB per la tua versione di Microsoft Windows:
    
    https://developer.android.com/studio/run/win-usb.html
3. Estrai l'archivio zip (ad esempio in `C:\ADB\`)
4. Apri la riga di comando (CMD) usando il menu start: Start > Esegui: cmd
5. Naviga nella directory dove hai estratto lo zip come segue. Useremo `C:\ADB\`
    ```
    c:
    cd ADB
    ``` 
6. Sul tuo smartphone apri le impostazioni e cerca la voce `Opzioni sviluppatore`. Se questa opzione non è già presente sul tuo telefono dovrai prima sbloccarla. Per farlo naviga in `Impostazioni > Info sul telefono`, lì individua `Numero build` (o simile) e toccalo 7 volte di seguito. Dovresti ora vedere una notifica che conferma che sei ora uno sviluppatore. Congratulazioni, le `Opzioni sviluppatore` sono ora disponibili nel tuo menu impostazioni.
7. Dentro le `Opzioni sviluppatore` cerca e attiva l'impostazione `Debug USB` (a volte chiamato semplicemente `Debug Android`).
8. Collega il tuo telefono al computer tramite cavo USB. I driver necessari dovrebbero ora essere scaricati e installati se non già presenti. Su Windows tutti i driver necessari dovrebbero essere scaricati automaticamente se hai seguito il passaggio [2] in precedenza. Sulla maggior parte dei sistemi GNU/Linux non è richiesta alcuna azione aggiuntiva. 
9. Se tutto ha funzionato, puoi ora tornare alla riga di comando e testare se il tuo dispositivo viene riconosciuto. Inserisci `adb devices -l`; dovresti vedere un output simile a:
    ```
    > adb devices -l
    List of devices attached
    * daemon not running. starting it now on port 5037 *
    * daemon started successfully *
    042111560169500303f4   unauthorized
    ```
10. Se il tuo dispositivo è etichettato come `unauthorized`, devi prima accettare un prompt sul tuo telefono che chiede se il debug tramite USB dovrebbe essere consentito. Quando riesegui `adb devices` dovresti ora vedere:
    ```
    > adb devices
    List of devices attached 
    042111560169500303f4    device
    ```   
11. Inizia a salvare il tuo log in un file sul tuo computer. Useremo `logcat.txt` in `C:\ADB\`. Inserisci semplicemente quanto segue (senza `> ` nella riga di comando):
    ```
    > adb -d logcat -v time | FINDSTR monocles_chat > logcat.txt
    ``` 
12. Ora riproduci il problema riscontrato.

13. Ferma il logging (`Ctrl+C`). Ora dai un'occhiata attenta al tuo file di log e rimuovi qualsiasi informazione personale e privata che potresti trovare prima di inviarlo insieme a una descrizione dettagliata del tuo problema, istruzioni su come riprodurlo. Puoi usare il tracker degli issue: [Issues](https://codeberg.org/Arne/monocles_chat/issues)
