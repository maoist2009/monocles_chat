# <img src="https://codeberg.org/monocles/monocles_chat/raw/branch/master/src/monocleschat/ic_launcher-playstore.png" width="30"> monocles chat

🇩🇪… [Deutsche Version der Readme hier verfügbar.](README.md) /  [Francais ici](README-fr.md) / [Italiano qui](README-it.md) / [Przeczytaj po polsku](README-pl.md)

monocles chat jest nowoczesnym i bezpiecznym klientem XMPP na Androida. Jest oparty na blabber.im oraz aplikacji [Conversations](https://github.com/siacs/Conversations), ale od siebie dodaje też wiele usprawnień i nowej funkcjonalności.
Zmiany te zostały wdrożone po to, żeby polepszyć używalność aplikacji, oraz ułatwić przeniesienie się z preinstalowanych, czy też w innym sensie popularnych komunikatorów. Poniżej znajdują się zrzuty ekranu:

<img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/00.png" width="200" /> <img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/01.png" width="200" /> <img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/02.png" width="200" /> <img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/03.png" width="200" /> <img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/04.png" width="200" /> <img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/05.png" width="200" /> <img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/06.png" width="200" /> <img src="https://codeberg.org/Arne/monocles_chat/raw/branch/master/fastlane/metadata/android/en-US/phoneScreenshots/07.png" width="200" />

Zrzuty ekranu zaprojektował Pigeonalley (https://pigeonalley.com)

### Download

<a href="https://f-droid.org/app/de.monocles.chat"><img src="https://f-droid.org/badge/get-it-on-pl.png" alt="Now on F-Droid" height="100"></a>
<a href="https://play.google.com/store/apps/details?id=eu.monocles.chat"><img src="https://codeberg.org/monocles/monocles_chat/raw/branch/master/art/GetItOnGooglePlay_Badge_Web_color_Polish.png" alt="Now on Google Play" height="100"></a>

### Ustawienia domyślne

monocles chat ma inne ustawienia domyślne niż blabber.im:

* nie pokazuje podglądu linków w konwersacjach
* nie pokazuje podglądu lokalizacji w konwersacjach
* nie pobiera automatycznie wszystkich załączników

### OTR

monocles chat wspiera także szyfrowanie metodą OTR! Mimo tego, że OTR może nie być łatwy w użyciu, tak ma wciąż swoje zalety:
<a href="https://pl.wikipedia.org/wiki/Off-the-record_messaging#Implementacja">https://pl.wikipedia.org/wiki/Off-the-record_messaging#Implementacja</a>

## Download
Aplikację monocles chat możesz pobrać z F-Droida.
Wersje beta możesz także pobrać z Codeberga: [Releases](https://codeberg.org/Arne/monocles_chat/releases) 

#### monocles chat nightly and beta

Wersje testowe aplikacji możesz pobrać z Codeberga: [Releases](https://codeberg.org/Arne/monocles_chat/releases) 

## Social Media
Obserwuj nas w serwisie <a rel="me" href="https://monocles.social/@monocles">monocles social</a>.

Istnieją także anglo- i niemieckojęzyczne pokoje XMPP, które skupiają się na udzielaniu wsparcia związanego z aplikacją monocles chat, jak i planowaniu przyszłych działań z nią związanych.

Jeżeli jesteś zainteresowany/-a pomocą w rozwijaniu aplikacji, możesz dołączyć do tego pokoju na XMPP (preferowany język angielski lub niemiecki): [development@conference.monocles.de](https://monocles.chat/)

Istnieje również pokój typu MUC (multi user chat), gdzie można zadawać pytania i uzyskać pomoc w przypadku napotkanych problemów. Sprawdź szczegóły poniżej.

## Jak mogę pomóc w tłumaczeniu?
Możesz stworzyć tzw. "merge request" i za jego pomocą dodawać kolejne pliki tłumaczeń, czy też publikować modyfikacje istniejących plików.

## Pomocy! Napotkałem/-am błędy!
Najłatwiejszym sposobem jest dołączenie na nasz pokój typu MUC (preferujemy język angielski lub niemiecki).
Link do pokoju: [support@conference.monocles.de](https://monocles.chat/)
Jeżeli nie będziemy w stanie pomóc, możesz otworzyć zgłoszenie związane z błędem/błędami [tutaj](https://codeberg.org/Arne/monocles_chat/issues). Opisz swój problem, kroki, żeby taki reprodukować, oraz udostępnij dzienniki zdarzeń. Sprawdź instrukcje dotyczące tworzenia dzienników zdarzeń poniżej.

### Jak tworzyć dzienniki zdarzeń? (adb)

#### GNU/Linux, macOS i inne systemy uniksowe:

1. Najpierw zainstaluj **A**ndroid **D**ebugging **B**ridge, jeżeli jeszcze nie jest on zainstalowany.
   ###### Debian i pochodne jak Ubuntu / Linux Mint
 ```
 sudo apt-get update
 sudo apt-get update adb
    # Debian Jessie lub starszy:
    # sudo apt-get install android-tools-adb
 ```
    ###### openSUSE 42.2 i 42.3
 ```
 sudo zypper ref
 sudo zypper install android-tools
 ```
    ###### openSUSE Tumbleweed
Tutaj musisz dodać następujące repo (np. przez Yast):
http://download.opensuse.org/repositories/hardware/openSUSE_Tumbleweed/

    alternatywnie można użyć `1 Click installer` 
https://software.opensuse.org/package/android-tools
###### inne systemy
zainstaluj adb używając metody odpowiedniej dla twojego systemu

2. Teraz otwórz terminal w wybranym katalogu lub przejdź do katalogu za pomocą `cd`.

3. Wykonaj kroki od [6] do [10] instrukcji dla systemu Windows.

4. Rozpocznij wypisywanie dziennika do pliku na komputerze. Będziemy używać pliku `logcat.txt`. Wpisz:
    `$ adb -d logcat -v time | grep -i "monocles chat" > logcat.txt`
 
5. Wykonaj pozostałe kroki [12] i [13] z instrukcji dla systemu Windows.


#### Windows:

1. Pobierz narzędzia Google SDK:

   https://developer.android.com/studio/releases/platform-tools.html
2. W niektórych przypadkach potrzebne są również sterowniki ADB_drivers dla posiadanej wersji systemu Microsoft Windows:

   https://developer.android.com/studio/run/win-usb.html
3. Rozpakuj archiwum zip (np. do `C:\ADB\`).
4. Otwórz wiersz poleceń (CMD) za pomocą menu Start: Start > cmd
5. Przejdź do katalogu, do którego rozpakowałeś/-aś zip w następujący sposób. Na potrzeby poradnika będzie to `C:\ADB\`.
 ```
 c:
 cd ADB
 ```
6. Na smartfonie otwórz ustawienia i wyszukaj pozycję `Opcje programisty`. Jeżeli ta opcja nie jest jeszcze dostępna w telefonie, należy ją wcześniej odblokować. Aby to zrobić, przejdź do `Ustawienia > Informacje o telefonie`, tam znajdź `Numer kompilacji` (lub podobny) i dotknij go 7 razy z rzędu. Powinieneś/powinnaś teraz zobaczyć powiadomienie, że "jesteś teraz programistą". `Opcje deweloperskie` od tej pory będą dostępne w ustawieniach.
7. Wewnątrz `Opcji deweloperskich` aktywuj ustawienie `USB-Debugging` (czasami nazywane po prostu `Android Debugging`).
8. Podłącz telefon do komputera za pomocą kabla USB. Niezbędne sterowniki powinny teraz zostać pobrane i zainstalowane, jeżeli jeszcze nie zostały zainstalowane. W systemie Windows wszystkie niezbędne sterowniki powinny zostać pobrane automatycznie, jeżeli wcześniej wykonałeś krok [2]. W większości systemów opartych na Linuxie nie są wymagane żadne dodatkowe czynności.
9. Jeżeli wszystko zadziałało, możesz teraz powrócić do wiersza poleceń i sprawdzić, czy urządzenie jest rozpoznawane. Wpisz `adb devices -l`; powinieneś/powinnaś zobaczyć wyjście podobne do:
 ```
    > adb devices -l
    List of devices attached
    * daemon not running. starting it now on port 5037 *
    * daemon started successfully *
    042111560169500303f4   unauthorized
    ```

10. Jeżeli twoje urządzenia są oznaczone jako `unauthorized`, musisz najpierw zaakceptować monit w telefonie z pytaniem, czy debugowanie przez USB powinno być dozwolone. Po ponownym uruchomieniu `adb devices` powinieneś/powinnaś zobaczyć:
    
   ```
    > adb devices
    List of devices attached 
    042111560169500303f4    device
    ```   

11. Rozpocznij rejestrację dzienników zdarzeń do pliku na komputerze. Na potrzeby poradnika będzie to plik `logcat.txt` w `C:\ADB\`. Wystarczy wpisać następujące polecenie (bez `> ` w wierszu poleceń):
    
```
> adb -d logcat -v time | FINDSTR monocles_chat > logcat.txt
 ```

12. Teraz odtwórz napotkany problem.

13. Zatrzymaj rejestrowanie dzienników (`Ctrl+C`). Teraz przyjrzyj się dokładnie plikowi dziennika i usuń wszelkie prywatne informacje, które możesz znaleźć, zanim wyślesz go wraz ze szczegółowym opisem problemu i instrukcjami, jak go odtworzyć. Możesz użyć narzędzia do śledzenia zgłoszeń GitHub: [Issues](https://github.com/kriztan/Monocles-Messenger/issues)