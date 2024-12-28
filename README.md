Siemka, 
w tym repozytorium jest kod programu do zbierania danych do wykorzystania w ramach mojej pracy magisterskiej. Kod jest jawny gdyby ktoś był ciekawy co się dzieje w tle działania aplikacji. Interfejs aplikacji jest w języku polskim.


Wymagania:
- Android w wersji minimum 10 do 14 włącznie
- Uprawnienia do aparatu (aplikacja polega na robieniu zdjęć)
- Uprawnienie do lokalizacji (śledzona jest pozycja użytkownika oraz zapisywana jest lokalizacja zrobienia zdjęcia) (lokalizacja musi być włączona podczas działania aplikacji)
- Uprawnienie do zapisu/odczytu w pamięci urządzenia (dane zebrane podczas działania aplikacji są zapisywane w pamięci urządzenia i również są z niego wczytywane podczas ponownego włączenia aplikacji)


- Aby zainstalować aplikację należy zezwolić na instalacje aplikacji z nieznanego źródła (aplikacja nie jest wystawiona na Play Store i instalowana jest bezpośrednio z pliku .apk). Jak to zrobić na swoim telefonie najlepiej jest to wygooglować. Może się okazać, że trzeba najpierw włączyć tryb developerski w telefonie, jeśli to będzie wymagane to najlepiej również wygooglować jak to zrobić.
- Aby zainstalować aplikację należy z głównego folderu tego repozytorium pobrać plik app-release.apk np. poprzez wejście na ten link https://github.com/JakubKonert/CollectDataStreetView/blob/main/app-release.apk (lub bezpośrednio z głównego folderu repozytorum) i kliknąc "trzy kropki" w prawym górnym rogu okna. Następnie wybrać opcję "Download". W tym miejscu zawsze będzie najnowsza/aktualna wersja aplikacji i zainteresowni będą poinformowni, że pojawiła się nowa wersja oraz co się zmieniło. Pobrany plik należy przerzucić na pamięć telefonu oraz go uruchomić, nastąpi wtedy instalacja aplikacji.
- Wszystkie pliki aplikacji są zapisane w pamięci urządzenia tam gdzie inne aplikacje. W moim przypadku ta ścieżka to "Android/Data/com.master.collectdatastreetview/files/"


Działanie aplikacji:
- Aplikacja polega na robieniu zdjęć np. podczas spaceru. Sama idea aplikacji jest podobna do streetview od Google. Dane te zostaną wykorzystane do nauczenia komputera podejmowania decyzji, aby jaknajefektywniej znaleźć przystanek autobusowy poprzez analizę obrazu.


Opis ekranów aplikacji:


Ekran główny:


![image](https://github.com/user-attachments/assets/07a61dfb-b7b5-4fb5-ba86-add457a5654a)


- Mapa - wyświetla zrobione zdjęcia, skupiska (cluster) oraz aktualną lokalizację użytkownika (aktualizowana co 1 sekundę).
- Menu - wyświetla dodatkowe opcje, które zostaną opisane później.
- Ikona kompasu - róg z literą N wskazuje kierunek, gdzie znajduje się północ. Ikona lubi szaleć, więc aby sprawdzić gdzie znajduje się północ to warto położyć poziomo telefon na ręce.
- Napis "Najbliższe zdjęcie" - wyświetla w metrach oraz kierunek w którym znajduje się najbliższe wykonane wcześniej zdjęcie.
- Napis "Liczba skupisk" - wyświetla aktualną liczbę skupisk.
Skupisko reprezentuje zdjęcia wykonane 9 metrów (domyślnie) od siebie.


- Napis "Log" - wyświetla informację o aktualnej pozycji użytkownika (długość i szerokość geograficzna) oraz służy do wyświetlania dodatkowych informacji np. informacji o błędach.
- Przycisk "Zrób zdjęcie" - pozwala na uruchomienie aparatu i zrobienia zdjęcia.


Przykładowy ekran główny (z przykładowwą trasą (dla celów pokazowych, mniejsza niż powinna być)):


![mainEkranPrzykład](https://github.com/user-attachments/assets/30dff461-2321-4319-8a6c-0c57226a5c4c)


 Opis menu:


![image](https://github.com/user-attachments/assets/b5a43e8c-2a2a-4f5c-907a-a0ce2b1612e5)


- Napis "Collect Data App" - to nazwa aplikacji i nie jest interaktywny.
- Opcja "Auto focus" - to przełącznik, który pozwala na włączenie lub wyłączenie centrowania mapy względem aktualnej pozycji użytkownika. Domyślnie co 1 sekundę odczytywana jest aktualna pozycja użytkownika i centrum mapy jest ustawiane na tą pozycję. Może to powodować, że mapa zacznie niewygodnie skakać. Tym przyciskiem można to wyłączyć.
- Opcja "Wczytaj zdjęcia" - pozwala wczytać wcześniej wykonane zdjęcia, podczas poprzedniego działania aplikacji. Pozwala to wczytać wcześniej zebrane dane i powrócić do zbierania danych na tym samym obszarze.
- Opcja "Twórz połączenia" - wyświetla ekran do tworzenia połączeń (zostanie on opisany szerzej poniżej). Pozwala on łączyć skupiska zdjęć liniami, które reprezentują połączenie między skupiskami na zasadzie z tego miejsca można przejść w to miejsce.
- Opcja "Pokaż graf" - wyświetla ekran do wyświetlenia stworzonego grafu oraz zapisania go (zostanie on opisany szerzej poniżej). Ekran ten służy do zapisania sesji zbierania danych do osobnego folderu, który reprezentuje dany przebieg.
- Opcja "Tryb Developerski" - to przełącznik dla celów testowych. Podczas normalnego użytkowania aplikacji nie jest on potrzebny. Po jego aktywowaniu zmienia on działanie ustawiania aktualnej lokalizacji użytkownika z tej podawanej przez lokalizację gps z telefonu, na miejsce na mapie gdzie kliknięto palcem/myszką.
- Napis "1.X" - Reprezentuje wersje aplikacji i nie jest interaktywny.


Ekran "Twórz połączenia":


![makeConnectionEmpty](https://github.com/user-attachments/assets/ec6f7f0a-1ade-490a-ac95-c4f2b380058b)


- Przy starcie tego ekranu są wczytywane zdjęcia oraz skupiska wykonane na ekranie głównym aplikacji. Jeśli wcześniej zapisano graf i nie zapisano przebiegu trasy to zostanie on wczytany, aby można było go dokończyć.
- Przycisk "Wróć" - wraca do głównego ekranu aplikacji.
- Przycisk "Zapisz graf" - zapisuje aktualny stan grafu do pliku graph.json. Plik ten przetrzymuje informacje o skupiskach oraz połączeniami między nimi.


Przykładowy ekran "Twórz połączenia":


![makeConnectionWithLines](https://github.com/user-attachments/assets/20ca72f6-d160-4cd8-8ed9-28d5276f24f5)


Ekran "Pokaż graf":


- Przycisk "Wczytaj graf" - wczytuje graf z pliku graph.json i wyświetla go na mapie. Graf ten jest tylko do odczytu i pozwala na wizualizacje go na mapie.
- Przycisk "Wróć" - wraca do głównego ekranu aplikacji.
- Przycisk "Zapisz trasę" - zapisuje daną trasę poprzez przeniesienia wszystkich aktualnych plików z danymi do osobnego folderu. Powinien on zostać wciśnięty tylko gdy chcemy zapisać trasę, gdy ją skończymy, zrobimy wszystkie połączenia i chce się ją zapisać. Dopiero gdy zapisze się poprzednią trasę można rozpocząć nową. Do wcześniej zapisane trasy nie można łatwo wrócić, więc trzeba być pewien że wszystko z nią związane zakończono i chce się to zrobić.


Przykładowy ekran "Pokaż graf":


![showGraph](https://github.com/user-attachments/assets/da377d9f-e405-4963-b29e-0fadf32e83b6)


Działanie aplikacji najłatwiej będzie przedstawić na przykładzie:
1. Uruchamiamy aplikację, w pierwszym oknie ukazuje się ekran główny aplikacji.
2. W pozycji zero swojej trasy należy zatrzymać się i zrobić minimum 2 zdjęcia. Przed sobą, za sobą i warto zrobić zdjęcia również z lewej strony i z prawej strony lub z innej perspektywy, która może nieść informacje. Telefon warto trzymać poziomo/horyzontalnie i po zrobieniu zdjęcia, ale przed jego zapisaniem położyć telefon płasko na ręce, aby kompas dobrze zapisał pozycje.
3. Należy przesunąć się o 20-50 metrów dalej (osobiście przesuwam się o około 35 metrów) i powtórzyć czynność opisaną w kroku 2. Odległość od najbliższego wykonanego zdjęcia jest wyświetlana nad mapą.
4. Kroki 2 i 3 należy powtórzyć, aż ilość skupisk wyniesie około 100 lub więcej. Informacja o aktualnej ilościu skupisk jest wyświetlana nad mapą. Dobrze by trasa nie była linią prostą, a np. figurą zamkniętą z skrzyżowaniami jak to pokazano na obrazu wyżej z przykładowym ekranem głównym.
   - Jeśli nie skończono robić zdjęć na trasie, a wyszło się z aplikacji to można wczytać swoje dane poprzez wybranie opcji "Wczytaj zdjęcia" z menu po ponowym włączeniu aplikacji.
5. Gdy wykonano wszystkie zdjęcia i liczba skupisk wynosi około 100 należy z menu wybrać opcję "Twórz połączenia". Opcja ta przeniesie do ekranu tworzenia połączeń między skupiskami.

   
7. Zostaną wczytane skupiska wykonanych wcześniej zdjęć (jeśli istnieje plik graph.json bo wcześniej tworzono połączenia ale nie skończono tego robić to zostanie on wczytany).
8. Połączenie skupisk wykonuje się poprzez kliknięcie na jedno z skupisk - wtedy jest one wybrane - i kliknięcie inne sąsiadujące skupisko. Wtedy zostanie stworzone połączenie symbolizowane przez czerwoną linię.
9. Kliknięcie 2 razy to samo skupisko anuluje jego wybranie.
10. Połączenie można usunąć poprzez kliknięcie na czerwoną linię symbolizującą dane połączenie. Linia wtedy zostanie usunięta z mapy.
11. Skupiska należy połączyć zgodnie z zasadą: z tego skupiska można dostać się do tego skupiska. Skupiska należy łączyć bezpośrednio z sąsiadującym tzn. jeśli mamy linię prostą złożoną z 3 skupisk to skupisko 1 można połączyć z skupiskiem 2, ale nie z skupiskiem 3.
12. Jedno skupisko może zostać połączone z wieloma skupiskami.
13. Po zakończeniu tworzenia połączeń lub gdy chce się zapisać swój postęp, należy kliknąć przycisk "Zapisz graf". Dane o aktualnych skupiskach i ich połączeniach zostaną zapisane do pliku graph.json.
14. Gdy stworzono wszystkie połączenia i chce się zapisać daną trasę (np. aby móc rozpocząć kolejną) należy z ekranu głównego aplikacji wejść do menu i wybrać opcję "Pokaż graf". Opcja ta przeniesie do ekranu z wizualizacją grafu i opcją zapisu trasy.


15. Po wejściu na ten ekran należy kliknąć przycisk "Wczytaj graf". Zostanie wczytany plik graph.json oraz zobrazowane dane w nim zawarte w postaci wykonanych wcześniej skupisk i ich połączeń.
16. Jeśli jest się pewien, że wszystko w ramach tej trasy jest skończone należy kliknąć przycisk "Zapisz trasę".
17. Pojawi sie okno dialogowe z pytaniem czy chce się na pewno zapisać trasę. Należy wybrać odpowiednią dla siebie opcje.
18. Jeśli wybierze się opcję "Tak" wszystkie dotychczas zebrane dane w ramach tej trasy zostaną przeniesione do osobnego folderu i można wtedy przystąpić do zbierania danych z kolejnej trasy powtarzając kroki od kroku 2 do kroku 18.


Porady i ewentualne problemy:
- Czasem mapa może pokazywać niebieskie tło. Najpewniej nie ustawiła prawidłowo swojego centrum i pokazuje środek jakiegoś zbiornika wodnego. Należy wtedy oddalić mapę, aż zacznie być widoczny ląd i przesunąć mapę palcami tak aby wskazywała interesujące nas centrum.
- Zdjęcia należy robić horyzontalnie/poziomo, a podczas zapisania zdjęcia mieć telefon położony poziomo na ręce. Ograniczy to niedokładność i chaotyczne działanie kompasu.


W razie wątpliwość, problemów lub pytań proszę o kontakt oraz z góry dziękuję za pomoc :)
