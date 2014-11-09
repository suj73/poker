package holdem;



import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.util.*;
import javax.imageio.ImageIO;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

//import javax.swing.*;
/**
 *
 * @author sune
 */
public class hand {
    private int handnummer;

    private ArrayList budliste = new ArrayList();//liste der vminderom udskrift for pokersites x bet 123 y raises 238 etc
    private int linje;//hvor er man i budmatrix. et mere simpelt forsøg end ovenstående
    private double[][] budmatrix = new double[40][10];//10x40 der holder alle samlede bud. var oprindelig tænkt som indeholde alle nødvendige data for DB hand
    private int[] bunke = new int [52];
 //private int[] bunke = {0, 1, 2, 3, 4, 5, 5, 3, 3, 2, 3, 4, 3, 1,32, 29,43, 21,35, 19,37, 4,9, 16,38, 20,10, 30, 22, 19, 7, 45, 41, 0, 13, 37, 14, 20, 11, 17, 5, 43, 9, 29, 24, 49, 23, 51, 28, 25, 10, 39};
    private int utg = 2;//under the gun positionen. Er faktisk ikke nødvendig for logikken så vidt jeg kan se !!
    private int button = 9;//
    private int smallpos = -1;//position af spiller med ret til knappen næste hånd
    private int bigpos = -1;
    private int antalbud = 1;//hvor mange gange er der buds/raised
    private spiller[] spillere = new spiller[10];//evt spillere på de 10 sæder
    private double[] winarray = new double[10];
    private double ante = 0;
    private double bb;//størrelse BB ikke position!
    private double[] blinds = new double[10] ; //matrix der angiver forced bets (blinds/straddles)
    private double minbet = 0;//minimum bud oveni. dvs bb til start af street. kan derefter vokse
    private double[] betaltialt = new double [10];//samlet alle streets, bruges når puljen deles (spillerom)
    private double[] betaltstreet = new double [10];//hvad har hver spiller betalt denne street
    private double[] spillerom = new double[10];
    private double currentbet = 0; //SAMLET bud denne street
    private double[][] prebets;//var tænkt som straddle, men straddle kan indgå i blinds array
    private double pot = 0;
    private double ekstra=0;//hvad der er "ekstra" i pulje. feks pennies fra forrige runde
    private int aktivspiller = 2;//default er tredje(2) spiller i rækken elller utg
    private int street = 0;//0=pre 1=flop 2=turn 3=river
    private boolean hu=false;
    private boolean race = false;
    private boolean slut = false;//slut bruges i grafik()
    private boolean foerhand=false;//har der været en hånd før denne på bordet?
    private int tilbage = 0;//værende tilbage i hånden. dvs ikke folded eller sitting out. all in er stadig tilbage
    private int tilbagestreet = 0;//kan agere på streeten. her er all in spillere ikke inkluderet.
    private int[] tilstand = new int[10]; //-1 sitting out. 0=ingen spiller. 1=folded. 2=kan agere. 3=har ageret. 4=all in
    private int grafik[] = new int [10];//holder status for 0:kort bagside 1:forside 2:folded
    private int level = 1;//skal være i spiller ikke her!
    private boolean simulering=false;
    //private ArrayList budliste = new ArrayList();
    


public hand()//spiller[] a, double[] b, long cspillere, blinds og hånd id nummer
       {
    }
public void nyhand(int handnummer)
            {

                fyldbord();
                this.handnummer=handnummer;
     blinds[0]=1;//Disse linjer skal kun sættes her midlertidigt- Herfra
     blinds[1]=2;//se setaktuel()
     bb = 2;
     minbet = bb;
     street=0;
     currentbet=2;//hertil
     race = false;
     antalbud=1;
     Arrays.fill(betaltstreet,0);
     Arrays.fill(betaltialt,0);
     Arrays.fill(winarray,0);
     budmatrix=new double[40][10];
     linje = 0;//se op. disse 5 nulstillinger burde måske være i feks starttilstand()
     //Arrays.fill(grafik,0);
     if(foerhand==false)
     { lodtraekknap();}
     else if(simulering==false||handnummer<100)//if slut == TRUE
     { starthand(); }
     else{
         for(int n=0;n<10;n++)
         {System.out.println(afrund(spillere[n].getroll()));}
     }
}
   public void starthand()
     {
//System.out.print(" starthand ");
        
     starttilstand();
     
     if(tilbage>2){
         hu=false;
         if(foerhand==true)//hvis det er første hånd er blind postet i nyhand()
         {postblinds();}
         else{
             foerhand=true;}
        
     bland();//her kan testes med egen bunke (pakkede kort)
     givkort();
     slut=false;
     
     }
     else{
         if(level!=2&&tilbage<2&&spillere[7].getstack()>0){
                level++;
                fyldbord();
    }
        }

     if(tilbage==2)
     {
         hu=true;
         postblindshu();
     bland();
     givkort();
     slut=false;}
     grafikupdate();
    }

     public void bet(double a)
                    {
           a=afrund(a);
           budmatrix[linje][aktivspiller]=a;
           updatebudliste(a);
           System.out.print(" bet "+a);

                    if(a-currentbet>=minbet){
                        minbet=a-currentbet;
                        betopen();//betopen åbner for alle eksklusive aktivspiller, justerer også tilbagestreet
                        }
                    else
                    {callopen();}

                    if(a-betaltstreet[aktivspiller]==spillere[aktivspiller].getstack())//spiller all in
                    {tilstand[aktivspiller]=4;}
                    else
                    {tilstand[aktivspiller]=3;}

       antalbud++;
       currentbet = a;
       spillere[aktivspiller].stackud(a-betaltstreet[aktivspiller]);
       updatestack(spillere[aktivspiller].getidnr(),spillere[aktivspiller].getstack());//gemmer i database, rækkefølge vigtig. se linje før
       potind(a-betaltstreet[aktivspiller]);
       betaltialt[aktivspiller]= betaltialt[aktivspiller]+a-betaltstreet[aktivspiller];
       betaltstreet[aktivspiller]=a;//igen. rækkefølge vigtig her, kan skabe logisk forvirring
       grafik[aktivspiller]=0;

    }
    public void call()
                    {
        System.out.print(" call ");
        
        if(spillere[aktivspiller].getstack()>(currentbet-betaltstreet[aktivspiller]))//spiller ikke all in
        {
            double currminusbetalt=afrund(currentbet-betaltstreet[aktivspiller]);
        double current=afrund(currentbet);
            tilstand[aktivspiller]=3;//NB: rækkefølgen af kommandoerne er vigtig her.
            spillere[aktivspiller].stackud(currminusbetalt);
            updatestack(spillere[aktivspiller].getidnr(),spillere[aktivspiller].getstack());//
            potind(currminusbetalt);
            betaltialt[aktivspiller]=(betaltialt[aktivspiller]+currminusbetalt);
            betaltstreet[aktivspiller]=current;
            budmatrix[linje][aktivspiller]=current;
            updatecallliste(current);
        }
        else
        {
            tilstand[aktivspiller]=4;//spiller ai. Har muligvis kompletet bet, men som regel ikke, da spiller er ai via call
            //betaltstreetadd(aktivspiller,spillere[aktivspiller].getstack());
            betaltstreet[aktivspiller]=betaltstreet[aktivspiller]+spillere[aktivspiller].getstack();
            budmatrix[linje][aktivspiller]=betaltstreet[aktivspiller];
            updatecallliste(betaltstreet[aktivspiller]);
            betaltialt[aktivspiller]=betaltialt[aktivspiller]+spillere[aktivspiller].getstack();//NB!
            potind(spillere[aktivspiller].getstack());            
            spillere[aktivspiller].setstack(0);
            updatestack(spillere[aktivspiller].getidnr(),0);//
        }
        grafik[aktivspiller]=0;
        tilbagestreet = tilbagestreet-1;

//System.out.print(betaltstreet[aktivspiller]);
    }
    public void fold()
                {
        System.out.print(" fold ");
       tilstand[aktivspiller]=1;
       grafik[aktivspiller]=2;
       tilbage = tilbage-1;
       tilbagestreet = tilbagestreet-1;
    }
        public void winbyfold()
    {
        //System.out.print("WIN BY DEFAULT");
        for(int n=0;n<10;n++){
            if(tilstand[n]>1){
                spillere[n].stackind(pot);
                updatestack(spillere[n].getidnr(),spillere[n].getstack());}}
        pot=0;
        slut= true;
        //setaktuel();
        if(simulering){
    gemhandresultat();
}
        
        nyhand(handnummer+1);
    }


    public void showdown()
    {
        //System.out.print("SHOWDOWN");
       // double[] spillerom = new double[10];//HAR statistisk interesse!! navnet siger det

for(int n=0; n<10;n++){
    if(tilstand[n]>1)//den skulle helst aldrig være 2 her, så > 2 burde være analog
    { spillerom[n]=spillerommetode(betaltialt,n)+ekstra;}
}
ekstra=0;
fordelpot(spillerom);
if(race==true)
{races();}//Har ikke rettet denne kluntede if-else da nyhand måske skal kaldes igen senere når programmet tilpasses
else{//kunne selvfølgelig laves som if(!race) {slut=true;} 
slut = true;
//setaktuel();
races();//er bare her for at vise kort
//spiller klikker selv nyhand når showdown er studeret. Medmindre det er AI simulering
}
if(simulering){
    gemhandresultat();
nyhand(handnummer+1);}
}
    public void setaktuel()//Det aktuelle bord. skal der eventuelt ændres på bordet. ny level i udfordring etc
    {
        System.out.println(" setaktuel ");
        if(spillere[7].getstack()==0)
        {
            fyldbord();
        }//level forfra med startsetup. forsøg tælles en op
        else if(spillere[7].getstack()==1000&&level<2)
        {
            level=2;
            fyldbord();
        }//er spiller 7 den eneste tilbage er level klaret
    }
    private void fordelpot(double[] a){//får array (spillerom) hvad spillerne spiller om hver især
//System.out.print(" fordelpot "+arrayToString(a));//a kan erstattes med spillerom, den blev gjort til field da har stat interesse 
unik u = new unik(bunke);
u.unikvaerdi();//regner hændernes værdi ud
int sejrhand=10000;//sættes højt da bedste hænder har mindst værdi (rsf = 1)
int vinderantal=0;
double potet = 0;//er også potto pottre etc
boolean igen;//er der flere pots. skal metoden køre igen

do{
for(int n=0;n<10;n++){
      if(a[n]!=0&&u.testunikplads(spillere[n].gethand())==sejrhand){
        vinderantal++;
        if(a[n]<potet)
        {potet=a[n];}
        }
   if(a[n]!=0&&u.testunikplads(spillere[n].gethand())<sejrhand){//går herned første gang. derfor den står nederst
       vinderantal=1;
       sejrhand = u.testunikplads(spillere[n].gethand());
       potet = a[n];
       }

}
    for(int n=0;n<10;n++){
        if(a[n]!=0&&u.testunikplads(spillere[n].gethand())==sejrhand){
            potud(nedrund(afrundnini(potet/vinderantal)));
            spillere[n].stackind(nedrund(afrundnini(potet/vinderantal)));
            updatestack(spillere[n].getidnr(),spillere[n].getstack());//
            winarray[n]=nedrund(afrundnini(potet/vinderantal));
            System.out.print(spillere[n].getnavn()+" vinder "+afrund(potet/vinderantal));//!!!slet afrundning her. det skjuler afrundningsproblemer der skal ses!!!
}}
for(int n=0;n<10;n++){
    if(a[n]>potet)
    {a[n]=a[n]-potet;}
    else
    {a[n]=0;}
}

sejrhand=10000;//Disse resettes så de er klar til eventuel fordeling af sidepot
vinderantal=0;
potet = 0;
igen=false;

for(int n=0;n<10;n++){//har spillere stadig noget tilgode fordeles sidepot
    if(a[n]>0)
    {igen=true;}
}
}
while(igen==true);

if(pot>0)
{ekstra=pot;}
    }

    public double spillerommetode(double[] a,int b) {//lille hjælpemetode til showdown
      //får array med spillernes investeringer og et spillernummer, returnerer hvad pågældende spiller om.

        double verdi=0;

       for(int n=0; n<10;n++){
          if(a[n]<a[b]){
              verdi=verdi+a[n];}
                      else
          {verdi=verdi+a[b];}
    }
        return verdi;
    }

    public void betopen()//kaldes når der bydes over minimum bud, således at der åbnes for nye bud
     {
//System.out.print(" betopen ");
        for(int n=0;n<10;n++){
            if(tilstand[n]==3&&n!=aktivspiller)
            {tilstand[n]=2;}
        }
        tilbagestreet=0;

        for(int n=0;n<10;n++){
            if(tilstand[n]==2&&n!=aktivspiller)
            {tilbagestreet++;}
        }
    }
    public void callopen()//kaldes i det tilfælde hvor der gåes all in uden at bettet kvalificerer til reraise
     {
//System.out.print(" callopen ");

tilbagestreet=0;

        for(int n=0;n<10;n++){
            if(tilstand[n]==2||tilstand[n]==3){
            if(n!=aktivspiller)
            {tilbagestreet++;}
            }
        } 
    }

    public void linjeskift(){
        //System.out.print("linjeskift");
        if((linje+1)%10!=9)
        {
            linje++;
        }
    }
    
    public void skift()//kontrollerer for alle-1 folded og om street færdig ellers skiftes spiller
    {//kaldes fra grafik
        //System.out.print(" skift ");
        if (tilbage==1)
        {winbyfold();}
        else if(tilbagestreet == 0)
        {streetdone();}
        else if(aktivspiller < 9)
        {aktivspiller++;}
        else {aktivspiller=0;
        linjeskift();}

}
    public void skiftto()//der skiftes bare spiller, no questions  asked. 
    {//kaldes fra her (hand)
        //System.out.print(" skiftto ");

        if(aktivspiller < 9)
        {aktivspiller++;}
        else {
            aktivspiller=0;//linjeskift kaldes ikke, da dette er i processen hvor der findes sb bb etc
        }
        }

        public int roter(int a)//der skiftes bare spiller INT, no questions  asked 
    {
        if(a < 9)
        {a++;}
        else
        {a=0;}//linjeskift kaldes ikke, da dette er i processen hvor der findes sb bb etc
        return a;
                }

        public void streetdone() {
            //System.out.print(" Streetdone ");
       uncalledbets();
       if(street<3)
       {nystreet();}
       else
       {showdown();}
    }
        
        public void nystreet(){
           // System.out.print(" nystreet ");
       street++;
       linje=street*10;
       antalbud=0;
       currentbet=0;
       Arrays.fill(betaltstreet,0);//betaltstreet nulstilles
       tilbagestreet = tilbage;//se nedenfor
       minbet=bb;
       if(hu==false){
       aktivspiller=smallpos;}
       else{
       aktivspiller=bigpos;}

       for (int n=9; n>-1; n--){
           if(tilstand[n]==3){//tilstand, grafik, &&tilstand[n]<4
               tilstand[n]=2;
               }
           if(tilstand[n]==4){//en all in spiller er ikke tilbage i streeten, men stadig tilbage i hånden
               tilbagestreet--;
               }
           }
       if(tilbagestreet < 2){//hvis kun en kan agere ved streetens start springes til næste street
          race=true;
           streetdone();//
           }
       //System.out.print("tilstand(nystreet) "+arrayToString(tilstand));
       //while(tilstand[aktivspiller]!=2)//dette skal ses på bare "slettet da gav tilstand 4 for alle ved sd og breakdown"
       //    skiftto();//men virker endnu efter at have slettet, ved ikke hvorfor jeg lavede den.
 }

       public void races(){//viser bare kort på spillere i hånden
//System.out.print(" races ");
     for(int n=0;n<10;n++){
         if(tilstand[n]>1)
         {grafik[n]=1;}
     }
     }
       public boolean getrace() {
       return race;
    }
       public int[] getbunke() {
       return bunke;
    }
       public int getstreet() {
       return street;
    }
       public void uncalledbets(){//Har en spiller budt mere end nummer to, returneres overskydende bet

         double[] maxto;
         maxto = Arrays.copyOf(betaltstreet, 10);//
         double max = maxelement(maxto);
         Arrays.sort(maxto);
         int vinder=indexOf(betaltstreet, max);//brugte binarySearch. det må man kun med sorteret, ellers undefined!!!!


         if(maxto[8]<maxto[9]){
betaltialt[vinder]=betaltialt[vinder]-(maxto[9]-maxto[8]);
         spillere[indexOf(betaltstreet, max)].stackind(maxto[9]-maxto[8]);
         updatestack(spillere[indexOf(betaltstreet, max)].getidnr(),spillere[indexOf(betaltstreet, max)].getstack());//
         potud(maxto[9]-maxto[8]);
         //System.out.print(arrayToString(betaltialt));
System.out.print(" retur til "+spillere[indexOf(betaltstreet,max)].getnavn()+" "+(maxto[9]-maxto[8]));
    }
}
       public void fyldbord()
               {//der skal oprettes et tomtobjekt med navn "seat available" eller lignende
           //System.out.print(" fyldbord ");   
   //            if(a!=7)
   //{spillere[a].setstrategy(3);}
               for(int n=0;n<10;n++){    
               placerDB(n);}                

       }


      public byte[] extractBytes (String ImageName) throws IOException {//IKKE I BRUG
 // open image
 File imgPath = new File(ImageName);
 BufferedImage bufferedImage = ImageIO.read(imgPath);

 // get DataBufferBytes from Raster
 WritableRaster raster = bufferedImage .getRaster();
 DataBufferByte data   = (DataBufferByte) raster.getDataBuffer();

 return ( data.getData() );
}
    public byte[] serialize(Object obj) throws IOException {//IKKE I BRUG
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ObjectOutputStream o = new ObjectOutputStream(b);
        o.writeObject(obj);
        return b.toByteArray();
    }

    public Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {//IKKE I BRUG
        ByteArrayInputStream b = new ByteArrayInputStream(bytes);
        ObjectInputStream o = new ObjectInputStream(b);
        return o.readObject();
    }

       public static byte[] imageToByteArray(BufferedImage image) throws IOException//IKKE I BRUG
{
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(image, "gif", baos);
    return baos.toByteArray();
}
       public BufferedImage byteArrayToImage(byte[] imageArray) throws IOException//IKKE I BRUG
{
    return ImageIO.read(new ByteArrayInputStream(imageArray));
}
      public void gembillede(String a, int b) throws IOException//a navn og b sæde
       {
           //System.out.print(" gembillede ");
   EntityManagerFactory  factory = Persistence.createEntityManagerFactory("Contact DB");
   EntityManager em = factory.createEntityManager();
   em.getTransaction().begin();
   Gtspiller dyr = em.find(Gtspiller.class, b);//       
   RandomAccessFile fi = new RandomAccessFile("D:portræt\\"+a+".gif", "r");   
   byte[] content = new byte[(int)fi.length()];
   fi.read(content);   
   dyr.setBillede(content);
   em.getTransaction().commit();
   em.close();
       }
       public byte[] givbillede(int a) throws IOException
       {
           //System.out.print("givbillede");
   EntityManagerFactory  factory = Persistence.createEntityManagerFactory("Contact DB");
   EntityManager em = factory.createEntityManager();
   em.getTransaction().begin();
   Gtspiller fi = em.find(Gtspiller.class, a);        
   em.close();
 
   return fi.getBillede();
       }
 public void resetdyr()
 {
     //System.out.print(" resetdyr ");
     EntityManagerFactory  factory = Persistence.createEntityManagerFactory("Contact DB");
   EntityManager em = factory.createEntityManager();
   em.getTransaction().begin();
for(int n=1;n<11;n++){
    Gtspiller dyr = em.find(Gtspiller.class, n); 
    dyr.setStack((double)100);
    //dyr.setBillede(spillere[n].getbillede());
}
   em.getTransaction().commit();
   em.close();
 }      
 public void placerDB(int a)//får int a og sætter spiller[a] til a+1 i DB
 {
    //System.out.print(" placerDB ");
    EntityManagerFactory  factory = Persistence.createEntityManagerFactory("Contact DB");
   EntityManager em = factory.createEntityManager();
   em.getTransaction().begin();
   Gtspiller dyr = em.find(Gtspiller.class, a+1); 
   spillere[a] = new spiller(a+1,dyr.getNavn(),dyr.getStack(),dyr.getRoll(),dyr.getStrategi());
   spillere[a].setbillede(dyr.getBillede());//ved ikke om dette er godt da det jo ikke skal hentes mere end en gang
   em.getTransaction().commit();
   em.close();
 }
 public void updatestack(int a, double b)//spiller a stack sættes til beløbet b
 {
     //System.out.print(" updatestack ");
     EntityManagerFactory  factory = Persistence.createEntityManagerFactory("Contact DB");
   EntityManager em = factory.createEntityManager();
   em.getTransaction().begin();
   Gtspiller dyr = em.find(Gtspiller.class, a); 
   dyr.setStack(b);
   em.getTransaction().commit();
   em.close();
 }
  public void updateroll(int a, double b)//spiller a roll sættes til beløbet b
 {
      //System.out.print(" updateroll ");
     EntityManagerFactory  factory = Persistence.createEntityManagerFactory("Contact DB");
   EntityManager em = factory.createEntityManager();
   em.getTransaction().begin();
   Gtspiller dyr = em.find(Gtspiller.class, a); 
   dyr.setRoll(b);
   em.getTransaction().commit();
   em.close();
 }
  public void updatestrategi(int a, int b)//spiller a stack sættes til beløbet b
 {
     //System.out.print(" updatestack ");
     EntityManagerFactory  factory = Persistence.createEntityManagerFactory("Contact DB");
   EntityManager em = factory.createEntityManager();
   em.getTransaction().begin();
   Gtspiller dyr = em.find(Gtspiller.class, a); 
   dyr.setStrategi(b);
   em.getTransaction().commit();
   em.close();
 }
  public void updatebillede(int a, byte[] b)//spiller a billede b (byte[])
 {
      //System.out.print(" updatebillede ");
     EntityManagerFactory  factory = Persistence.createEntityManagerFactory("Contact DB");
   EntityManager em = factory.createEntityManager();
   em.getTransaction().begin();
   Gtspiller dyr = em.find(Gtspiller.class, a); 
   dyr.setBillede(b);
   em.getTransaction().commit();
   em.close();
 }



       public void lodtraekknap(){
System.out.print(" lodtrækning ");
          int vindpos=9;//vinder af knappen
          int vindkort=900;//det bedste kort (højest ellers spar ruder hjerter klør)
      bland();
      for(int n=0;n<10;n++){
          if(spillere[n].getaktiv()==true&&spillere[n].getstack()>bb)
          {grafik[n]=1;}
          else
          {grafik[n]=2;}
       }

      for(int n=0;n<10;n++){
          if(grafik[n]==1&&bunke[n]<vindkort){
              vindkort=bunke[n];
              vindpos=n;
      }
       }
      button=vindpos;
      aktivspiller=button;
      do
      {skiftto();}
while(grafik[aktivspiller]!=1);
      
    if(simulering){
      autopost();}
     //her kan indføres en
    }
       public void gemhandresultat(){
           System.err.println("GEM hånd"+handnummer);
       for(int n=0;n<10;n++){
       updateroll(n+1,spillere[n].getroll()-100+spillere[n].getstack());//roll updateres med det vundne/tabte i hånden
       updatestack(n+1,100);//alle starter den nye hånd med fuld stack
       }
               }
       public void autopost(){
       if(smallpos==-1){
            System.out.print("spiller "+aktivspiller+" accepterer smallblind pos");
            smallpos=aktivspiller;
            findbb();
            //tegnaktiv();
            autopost();}
        else{
            System.out.print("spiller "+aktivspiller+" accepterer bigblind pos");
            postsbbb(aktivspiller);
            //her kan ventes på straddle 
            starthand();
            //koer();
            //tegnbord();
            //tegnkort();
            //tegnstacks();
            
            //setgliderstats();
            //tegnaktiv();
        }
       
       }
       public void findbb(){
           //System.out.print(" fidbb ");
                 do
                 {skiftto();}
while(grafik[aktivspiller]!=1);
}
        public void starttilstand()
                                    {
            System.out.print(" starttilstand ");//+arrayToString(tilstand)
     tilbage = 0;
     tilbagestreet = 0;

    for (int n=0;n<10;n++){//vil altid kun opfylde en af disse 4 if sætninger
        if(spillere[n].getstrategi()==-1){//Tom sæde
            tilstand[n]=0;
            }
        else if(spillere[n].getstack()> 0)//opri. else if(spillere[n].getstack() -  blinds[1] - ante > 0)
           { tilstand[n]=2;
             tilbage++;
             tilbagestreet++;}
        else//bustet spiller. opr. else if(spillere[n].getstack() == 0)
        {tilstand[n]=-1;}//nedenstående flyttet til postblinds metode for at vide hvor blinds er og sætte all in for evt non completet spillere
        //else if(spillere[n].getstack() != 0 && spillere[n].getstack() - blinds[1] - ante < 0)//en spiller med under en bb kan fremtvinge showdown :(
         //  {tilstand[n]=4;//officielle regler er all in pre deal (hvis aktiv), men vil ikke følge dem her
          //  tilbage++;}//en all in spiller behandles som værende tilbage i hånden men ikke på streeten
    }
    
}
        public void updateblindliste(String a,double b)//får string enten smallblind eller bigblind
        {
            String foo;
            foo = spillere[aktivspiller].getnavn()+" posts "+b;
            budliste.add(foo); 
        }
        public void updatefoldliste()
        {
            String bar;
            bar = spillere[aktivspiller].getnavn()+" FOLDS";
            budliste.add(bar); 
        }
       public void updatecallliste(double a)
       {
           String baz;
           baz = spillere[aktivspiller].getnavn()+" CALLS "+a;
           budliste.add(baz); 
       }
  public void updatebudliste(double a){
      String qux;
  qux = spillere[aktivspiller].getnavn()+" "+givbudnavn()+"s to "+a;
  budliste.add(qux);  
  }
  public String givbudnavn()        {
    String name;
    if(antalbud==0)
    {name="BET";}
      else if(antalbud == 1)
      {name="RAISE";}
      else if(antalbud == 2)
      {name="RERAISE";}
     else 
      {name=Integer.toString(antalbud-1)+"XRERAISE";}//;
      return name;
}      
public boolean getslut()        {
    return slut;
}
public void setfoerhand(boolean a){
    foerhand=a;
}
public boolean getfoerhand()        {
    return foerhand;
}
public int getsmallpos()
           {
    return smallpos;
}
public void setsmallpos(int a)
           {
    smallpos=a;
}
public int getbutton()
           {
    return button;
}
public int getbigpos()
           {
    return bigpos;
}
public void setbigpos(int a)
           {
    bigpos=a;
}
public int getantalbud(){
return antalbud;
}
public int gettilbagestreet(){
return tilbagestreet;
}
public int gettilbage(){
return tilbage;
}
public int gettilstand(int a)
           {
    return tilstand[a];
}
public int getgrafik(int a)
           {
    return grafik[a];
}
public void setgrafik(int a, int b)
           {
    grafik[a]= b;
}
public double getpot()
        {
    return pot;
}
public void potind(double a)
        {
    pot = pot+a;

}
public void potud(double a)
        {
    pot = pot-a;
}
public void potnul()
        {
    pot = 0;
}
public void betaltstreetadd(int a, double b){
    betaltstreet[a] = betaltstreet[a]+b;
}
public double getbetaltstreet(int a){
    return betaltstreet[a];
}
public void betaltstreetnul(){//sætter alle værdier i betaltstreet lig nul. vist ikke i brug
    Arrays.fill(betaltstreet,0);
}
public double[] getbetaltialt(){
return betaltialt;
}
public double getbetaltialt(int n){
return betaltialt[n];
}
public int gethandnummer ()
        {
   return handnummer;
}
public void setminbet (double a){
    minbet = a;
}
public double getminbet ()
        {
   return minbet;
}

    public boolean getSimulering() {
        return simulering;
    }

    public void setSimulering(boolean simulering) {
        this.simulering = simulering;
    }

public void setlevel(int a){
level=a;
}
public int getlevel(){
return level;
}


public double[] muligheder(){//returnerer fold -1=ikke "klog" eller 1 for mulig, call beløb, min bet, max bet -1 hvis bet ikke mulig
double mulig[]= new double[4];

if(currentbet-betaltstreet[aktivspiller]==0)//ingen bud så fold "dumt"
{
    mulig[0]=-1;
    mulig[1]=0;
}
else{
     mulig[0]=1;
if(spillere[aktivspiller].getstack()>(currentbet-betaltstreet[aktivspiller]))//call, spiller ikke all in
{
mulig[1]=currentbet;    
}
else{
mulig[1]=betaltstreet[aktivspiller]+spillere[aktivspiller].getstack();
}
}
if(spillere[aktivspiller].getstack()<=currentbet-betaltstreet[aktivspiller]||tilstand[aktivspiller]!=2)
        {
    mulig[2]=-1;
    mulig[3]=-1;
}
else{//kan hæve, har mere end budet
mulig[2]=Math.min(currentbet+minbet,currentbet+spillere[aktivspiller].getstack());    
mulig[3]=spillere[aktivspiller].getstack()+betaltstreet[aktivspiller];
}
//System.out.print(" Mulig "+spillere[aktivspiller].getnavn()+" "+arrayToString(mulig)+" tilstand"+tilstand[aktivspiller]);
return mulig;
}
public double[] getblinds ()
        {
   return blinds;
}
public void setbb (double a){
    bb = a;
}
public double getbb ()
        {
   return bb;
}
public void setaktiv (int a){
    aktivspiller = a;
}
public int getaktiv ()
        {
   return aktivspiller;
}
public void setcurrentbet (double a){
    currentbet = a;
}
public double getcurrentbet ()
        {
   return currentbet;
}
public void setspillere(spiller a, int b)
{
   spillere[b]= a;
}
public spiller getspiller (int a)
        {
   return spillere[a];
}
public double[] getwinarray()
{
return winarray;
}
public String getnavn (int a)
        {
    return spillere[a].getnavn();
}

   public boolean afsluttet()//bliver ikke brugt. returnerer false hvis stadig 2 eller mere aktive
              {
       boolean a = false;
       if(tilbage<2)
       {
           a = true;
       }
       return a;
   }

   public final void bland() {
        {
            int[] bunketo = new int[52];
            for (int k = 0; k < bunke.length; k++) {
                bunke[k] = -k - 1;
            }
            for (int i = 52; i > 0; i = i - 1) {
                bunketo[i - 1] = (int) (-1 - i * Math.random());//alle de nødvendige informationer skabes i denne linje
                for (int j = 51; j >= 0; j = j - 1) {
                    if (bunke[j] == bunketo[i - 1]) {
                        bunke[j] = i - 1;
                    }

                    if (bunke[j] < bunketo[i - 1]) {
                        bunke[j] = bunke[j] + 1;
                    }
                }
            }
        }
    }
   public int tagkort(int a)
           {
       return bunke[a];
       }
   public final void givkort()
{
for(int n = 0;n<10;n++){//
if(tilstand[n]>1){
    int[] temp = new int[2];//da denne stod uden for løkken gav det et mærkeligt resultat, alle havde hånd 9
    temp[0] = bunke[5+2*n];//
    temp[1] = bunke[6+2*n];//
    Arrays.sort(temp);//det vælges at sortere holekortene
spillere[n].sethand(temp);
}}
}

public void postblindsto()//ikke i brug !! SKAL IKKE SLETTES, DA DEN BARE ER IMPLEMENTERINGEN AF ET ANDET BLIND PRINCIP
{//Minder om simplified. Dog rykker button altid til tidligere sb, så ingen trækker helt frinummer 
    //System.out.print(" Blinds2 ");//tilgengæld kan button spekulere (især i tournaments)
    int temppos=bigpos;
    button=smallpos;//button altid til tidligere smallpos om der er spiller eller ej
    if(tilstand[bigpos]>1)//tidligere big har retten til small
    {smallpos=bigpos;}
    else{
        while(tilstand[temppos]<1)
        {temppos=roter(temppos);}
    smallpos=temppos;
    }
    temppos=roter(temppos);
     while(tilstand[temppos]<1)
     {temppos=roter(temppos);}
    bigpos=temppos;
        temppos=roter(temppos);
     while(tilstand[temppos]<1)
     {temppos=roter(temppos);}
    utg=temppos;
aktivspiller=utg;
           //System.out.print(" "+spillere[smallpos].getnavn()+" posts smallblind " + blinds[0]);
           spillere[smallpos].stackud(blinds[0]);//
           updatestack(spillere[smallpos].getidnr(),spillere[smallpos].getstack());
           betaltialt[smallpos] = blinds[0];
           betaltstreet[smallpos] = blinds[0];
           updateblindliste("SMALLBLIND",blinds[0]);
           potind(blinds[0]);

           //System.out.print(" "+spillere[bigpos].getnavn()+" posts bigblind " + blinds[1]);
           spillere[bigpos].stackud(blinds[1]);//
           updatestack(spillere[bigpos].getidnr(),spillere[bigpos].getstack());
           betaltialt[bigpos] = blinds[1];
           betaltstreet[bigpos] = blinds[1];
           updateblindliste("BIGBLIND",blinds[1]);
           potind(blinds[1]);
}
public void postblindshu(){
System.out.print("HU-blinds");
int temppos=bigpos;

    temppos=roter(temppos);//under HU skal BB ifl reglerne altid gå videre til næste levende billede
     while(tilstand[temppos]<1)//Dette kan give "mærkelig" bevægelse af dealerknap når spillet går fra 3 til 2 spillere
     {temppos=roter(temppos);}
    bigpos=temppos;
    
        temppos=roter(bigpos);//Der roteres videre og positionen findes der svarer til smallpos, button og aktivspiller
     while(tilstand[temppos]<1)//
     {temppos=roter(temppos);}
    aktivspiller = temppos;//preflop agerer dealer/sb først når det er HU
    smallpos=temppos;
    button=temppos;
             if(tilstand[smallpos]>1){//er smallblind stadig "levende"
           //System.out.print(" "+spillere[smallpos].getnavn()+" posts smallblind " + blinds[0]);
           spillere[smallpos].stackud(blinds[0]);//
           updatestack(spillere[smallpos].getidnr(),spillere[smallpos].getstack());
           betaltialt[smallpos] = blinds[0];
           betaltstreet[smallpos] = blinds[0];
           budmatrix[linje][smallpos]=blinds[0];
           updateblindliste("SMALLBLIND",blinds[0]);
           potind(blinds[0]);
}
           //System.out.print(" "+spillere[bigpos].getnavn()+" posts bigblind " + blinds[1]);
           spillere[bigpos].stackud(blinds[1]);//
           updatestack(spillere[bigpos].getidnr(),spillere[bigpos].getstack());
           betaltialt[bigpos] = blinds[1];
           betaltstreet[bigpos] = blinds[1];
           budmatrix[linje][bigpos]=blinds[1];
           updateblindliste("BIGBLIND",blinds[1]);
           potind(blinds[1]);
}

public void postblinds()//Kaldes fra hand
{//Denne blindmetode svarer til "dead button". ingen slipper, ikke altid sb, button kan spekulere (især i tours)
    System.out.print(" Blinds ");
    int temppos=bigpos;
    button=smallpos;//button altid til tidligere smallpos om der er spiller eller ej
    smallpos=bigpos;
    
    temppos=roter(temppos);
     while(tilstand[temppos]<1)
     {temppos=roter(temppos);}
    bigpos=temppos;
        temppos=roter(temppos);
     while(tilstand[temppos]<1)
     {temppos=roter(temppos);}
    utg=temppos;
aktivspiller=utg;
         if(tilstand[smallpos]>1){//er smallblind stadig "levende"
             if(spillere[smallpos].getstack()>blinds[0]){//kan complete
           //System.out.print(" "+spillere[smallpos].getnavn()+" posts smallblind " + blinds[0]);
           spillere[smallpos].stackud(blinds[0]);//
           updatestack(spillere[smallpos].getidnr(),spillere[smallpos].getstack());
           betaltialt[smallpos] = blinds[0];
           betaltstreet[smallpos] = blinds[0];
           budmatrix[linje][smallpos]=blinds[0];
           updateblindliste("SMALLBLIND",blinds[0]);
           potind(blinds[0]);
             }
             else{//Small blind kan ikke complete
                 betaltialt[smallpos] = spillere[smallpos].getstack();
           betaltstreet[smallpos] = spillere[smallpos].getstack();
           budmatrix[linje][smallpos]=spillere[smallpos].getstack();
           updateblindliste("SMALLBLIND",spillere[smallpos].getstack());
           potind(spillere[smallpos].getstack());
           updatestack(spillere[smallpos].getidnr(),0);
           spillere[smallpos].setstack(0);
           tilstand[smallpos]=4;
           tilbagestreet--;//er sat til tilbagestreet i starttilstand så bliver sat tilbage
             }
}
         if(spillere[bigpos].getstack()>blinds[1]){//big blind kan complete
           //System.out.print(" "+spillere[bigpos].getnavn()+" posts bigblind " + blinds[1]);
           spillere[bigpos].stackud(blinds[1]);//
           updatestack(spillere[bigpos].getidnr(),spillere[bigpos].getstack());
           betaltialt[bigpos] = blinds[1];
           betaltstreet[bigpos] = blinds[1];
           budmatrix[linje][bigpos]=blinds[1];
           updateblindliste("BIGBLIND",blinds[1]);
           potind(blinds[1]);
         }
         else{//big blind kan ikke complete
             betaltialt[bigpos] = spillere[bigpos].getstack();
           betaltstreet[bigpos] = spillere[bigpos].getstack();
           budmatrix[linje][bigpos]=spillere[bigpos].getstack();
           updateblindliste("BIGBLIND",spillere[bigpos].getstack());
           potind(spillere[bigpos].getstack());
             spillere[bigpos].setstack(0);//
           updatestack(spillere[bigpos].getidnr(),0);
           tilstand[bigpos]=4;
           tilbagestreet--;//er sat til tilbagestreet i starttilstand så bliver sat tilbage
         }
         //else if(spillere[n].getstack() != 0 && spillere[n].getstack() - blinds[1] - ante < 0)//en spiller med under en bb kan fremtvinge showdown :(
         //  {tilstand[n]=4;//officielle regler er all in pre deal (hvis aktiv), men vil ikke følge dem her
          
}

   public void postsbbb(int a)//får big blind pos, smallpos er fundet
  {//(Kaldes fra grafik postblind knappen)
       
bigpos=a;
System.out.print(" postsbbb "+spillere[bigpos].getidnr());
           System.out.print(" "+spillere[smallpos].getnavn()+" posts small blind " + blinds[0]);
           spillere[smallpos].stackud(blinds[0]);//
           updatestack(spillere[smallpos].getidnr(),spillere[smallpos].getstack());
           betaltialt[smallpos] = blinds[0];
           betaltstreet[smallpos] = blinds[0];
           budmatrix[linje][smallpos]=blinds[0];
           potind(blinds[0]);

           System.out.print(" "+spillere[bigpos].getnavn()+" posts big blind " + blinds[1]);
           spillere[bigpos].stackud(blinds[1]);//
           updatestack(spillere[bigpos].getidnr(),spillere[bigpos].getstack());
           betaltialt[bigpos] = blinds[1];
           betaltstreet[bigpos] = blinds[1];
           budmatrix[linje][bigpos]=blinds[1];
           potind(blinds[1]);

       if(spillere[bigpos].getstack()==0)
       {tilstand[bigpos]=4;}

           
           setutgto();

     }
   public final void setutgto(){//finder reelt aktivspiller utg er vist ikke i brug rent logisk se ovenfor
       //System.out.print(" setutgto ");
       aktivspiller = bigpos;
       skiftto();
       while(grafik[aktivspiller]!=1)
       {skiftto();}

       utg=aktivspiller;
   }
   public final void setutg()//IKKE I BRUG bestemmer under the gun udfra blinds 
{//
       //System.out.print(" setutg ");
       button = smallpos;
       aktivspiller = bigpos;
 
try{
while(betaltstreet[aktivspiller]!=0||tilstand[aktivspiller]<2) {
        skiftto();
    }
    utg = aktivspiller;}
catch (ArrayIndexOutOfBoundsException e) {
    System.out.print("blinds og UTG kan ikke sættes efter normale regler, da antal spillere er under 3 ");
    }
     
    }
       public void grafikupdate()
            {
           //System.out.print(" grafikupdate"+aktivspiller);
        for(int n=0;n<10;n++){
            if(tilstand[n]<2) {
                grafik[n]=2;
            }
            else {
                grafik[n]=0;
            }
               }
         if(spillere[aktivspiller].getstrategi()==1) {
                    grafik[aktivspiller]=1;
                }
    }
       public static String arrayToString(int[] a) {//
        String tekst = "";
        for (int i = 0; i < a.length; i = i + 1) {
            tekst = tekst.concat(a[i] + " ");
        }
        return tekst;
    }
       public String arrayToString(double[] a) {//import java.util.Arrays her findes også sort hurtig metode
        String tekst = "";
        for (int i = 0; i < a.length; i = i + 1) {
            tekst = tekst.concat(a[i] + " ");
        }
        return tekst;
    }
           public String arrayToString(double[][] a) {//import java.util.Arrays her findes også sort hurtig metode
        String tekst = "";
        for (int i = 0; i < a.length; i = i + 1) {
            tekst = tekst.concat(arrayToString(a[i]) + " ");
        }
        return tekst;
    }
                      public String arrayToString(int[][] a) {//import java.util.Arrays her findes også sort hurtig metode
        String tekst = "";
        for (int i = 0; i < a.length; i = i + 1) {
            tekst = tekst.concat(arrayToString(a[i]) + " ");
        }
        return tekst;
    }
           public int indexOf(double[] a, double n)//giver ikke index men index -1
    {
        int index = -1;
        for (int i = 0; i < a.length; i = i + 1) {
            if (a[i] == n) {
                index = i;//indsaet i+1 hvis indexof navnet skal være retvisende
            }
        }
        return index;
    }
        public int minelement(int[] a) {
        int mindste = a[0];
        for (int i = 0; i < a.length; i = i + 1) {
            if (a[i] < mindste) {
                mindste = a[i];
            }
        }
        return mindste;
    }
        public double minelement(double[] a) {
        double mindste = a[0];
        for (int i = 0; i < a.length; i = i + 1) {
            if (a[i] < mindste) {
                mindste = a[i];
            }
        }
        return mindste;
    }
            public double maxelement(double[] a) {
        double stoerste = a[0];
        for (int i = 0; i < a.length; i = i + 1) {
            if (a[i] > stoerste) {
                stoerste = a[i];
            }
        }
        return stoerste;
    }
            public double effstack(){//retur effektiv stack set fra aktivspilllers synspunkt
                
             double effstack;
             double maxmodstander = 0;
             double reststack = spillere[aktivspiller].getstack()+betaltstreet[aktivspiller];
             
for(int n=0;n<10;n++) {
                    if(tilstand[n]>1&&n!=aktivspiller) {
        if((spillere[n].getstack()+betaltstreet[n])>maxmodstander) {
                            maxmodstander= spillere[n].getstack()+betaltstreet[n];
                        }
    }
                }
         effstack = Math.min(reststack,maxmodstander);
         return effstack;
        
}

              public double effstack(int a){//retur effektiv stack set fra spiller a's synspunkt
                
             double effstack;
             double maxmodstander = 0;
             double reststack = spillere[a].getstack()+betaltstreet[a];
             
for(int n=0;n<10;n++) {
                      if(tilstand[n]>1&&n!=a) {
        if((spillere[n].getstack()+betaltstreet[n])>maxmodstander) {
                              maxmodstander= spillere[n].getstack()+betaltstreet[n];
                          }
    }
                  }
         effstack = Math.min(reststack,maxmodstander);
         return effstack;
        
}      
             public double[] effstackarray(){//retur array af effektiv stack set pågældende spilllers synspunkt
                //kan godt være større end den aktive spiller
             double[] effstack = new double[10];
             
for(int n=0;n<10;n++){
    if(tilstand[n]>1){
            effstack[n]=effstack(n);}
    else{
        effstack[n]=0;}}
         return effstack;
        
}
            public double nedrund(double num) {
double result = num * 100;
result = Math.floor(result);//floor runder ned, round til nærmeste, ceil op
result = result / 100;
return result;
}
              public double afrundnini(double num) {//1,999999... må ikke nedrundes
double result;
result=Math.round(num*10000.0)/10000.0;
  return result;
}
  public double afrund(double num) {
double result;
result=Math.round(num*100.0)/100.0;
  return result;
}
}
