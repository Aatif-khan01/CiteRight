package com.citeright.ui;
import com.citeright.model.*;
import com.citeright.database.AnnotationDAO;
import com.citeright.formatter.*;
import com.citeright.service.PdfService;
import com.citeright.service.ReadingTimerService;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import java.util.*;

public class PdfViewerPane extends BorderPane {
    private final PdfService pdf;
    private final AnnotationDAO dao;
    private final AnnotationToolbar toolbar;
    private final ReadingTimerService timerService;
    private String path; private int pdfId, pg=1, total=0;
    private float zoom=1.5f;
    private boolean isUpdatingZoom=false;
    private Publication pub;
    private ImageView pageIV; private Canvas annCanvas;
    private StackPane pageStack;
    private Label pgLbl,zoomLbl,titleLbl,progressLbl;
    private Spinner<Integer> pgSpin;
    private VBox annListBox,thumbBox;
    private Slider zoomSlider;
    private HBox searchBar;
    private TextField searchField;
    private Label searchInfo;
    private String searchQuery="";
    private List<Integer> searchResults=new ArrayList<>();
    private int searchIdx=-1;
    private double penSX,penSY;
    private StringBuilder curStroke;
    private Runnable onBack;
    private final Set<Integer> bookmarks=new TreeSet<>();
    private final Set<Integer> visitedPages=new HashSet<>();
    private boolean darkMode=false,sepiaMode=false;
    private ProgressBar progressBar;
    private ScrollPane thumbScroll;
    private ToggleGroup viewModeGroup;
    public PdfViewerPane(PdfService p,AnnotationDAO d){
        pdf=p;dao=d;toolbar=new AnnotationToolbar();
        timerService = new ReadingTimerService();
        buildUI();
    }
    public void setOnBack(Runnable r){
        onBack = () -> {
            timerService.pause();
            if (r != null) r.run();
        };
    }
    private void buildUI(){
        setStyle("-fx-background-color:#12122a;");
        VBox rp = new VBox(0);
        VBox top=new VBox(0);
        HBox tBar=new HBox(10);tBar.setAlignment(Pos.CENTER_LEFT);tBar.setPadding(new Insets(6,16,6,16));
        tBar.setStyle("-fx-background-color:#0e0e22;-fx-border-color:#2a2a4a;-fx-border-width:0 0 1 0;");
        Button back=new Button("\u2190 Back");
        back.setStyle("-fx-background-color:#2a2a4a;-fx-text-fill:#aac;-fx-font-size:11px;-fx-padding:5 12;-fx-background-radius:5;-fx-cursor:hand;");
        back.setOnAction(e->{if(onBack!=null)onBack.run();});
        titleLbl=new Label("PDF Viewer");titleLbl.setStyle("-fx-text-fill:#fff;-fx-font-size:13px;-fx-font-weight:bold;");
        Region s1=new Region();HBox.setHgrow(s1,Priority.ALWAYS);

        // Timer Label
        Label timerLbl = new Label("00:00");
        timerLbl.setStyle("-fx-text-fill:#f5a623;-fx-font-size:12px;-fx-font-weight:bold;-fx-background-color:#2a2a4a;-fx-padding:4 8;-fx-background-radius:4;");
        timerLbl.textProperty().bind(timerService.formattedTimeProperty());
        Button timerToggle = new Button("⏸");
        timerToggle.setStyle("-fx-background-color:transparent;-fx-text-fill:#aac;-fx-cursor:hand;");
        timerToggle.setOnAction(e -> {
            timerService.toggle();
            timerToggle.setText(timerService.runningProperty().get() ? "⏸" : "▶");
        });

        Button citBtn=new Button("\uD83D\uDCCB Cite");
        citBtn.setStyle("-fx-background-color:#6c5ce7;-fx-text-fill:#fff;-fx-font-size:10px;-fx-padding:5 10;-fx-background-radius:5;-fx-cursor:hand;");
        citBtn.setOnAction(e->copyCitation());
        Button expBtn=new Button("\uD83D\uDCE4 Export");
        expBtn.setStyle("-fx-background-color:#4a6cf7;-fx-text-fill:#fff;-fx-font-size:10px;-fx-padding:5 10;-fx-background-radius:5;-fx-cursor:hand;");
        expBtn.setOnAction(e->exportPdf());
        viewModeGroup=new ToggleGroup();
        ToggleButton darkBtn=new ToggleButton("\uD83C\uDF19");darkBtn.setTooltip(new Tooltip("Dark Mode"));
        darkBtn.setStyle("-fx-background-color:#2a2a4a;-fx-text-fill:#aac;-fx-padding:4 8;-fx-background-radius:4;-fx-cursor:hand;");
        darkBtn.setToggleGroup(viewModeGroup);
        darkBtn.setOnAction(e->{darkMode=darkBtn.isSelected();sepiaMode=false;renderPage();});
        ToggleButton sepBtn=new ToggleButton("\u2615");sepBtn.setTooltip(new Tooltip("Sepia Mode"));
        sepBtn.setStyle("-fx-background-color:#2a2a4a;-fx-text-fill:#aac;-fx-padding:4 8;-fx-background-radius:4;-fx-cursor:hand;");
        sepBtn.setToggleGroup(viewModeGroup);
        sepBtn.setOnAction(e->{sepiaMode=sepBtn.isSelected();darkMode=false;renderPage();});
        tBar.getChildren().addAll(back,titleLbl,s1,timerLbl,timerToggle,citBtn,expBtn,darkBtn,sepBtn);
        toolbar.setStyle("-fx-background-color:#1a1a36;-fx-border-color:#2a2a4a;-fx-border-width:0 0 1 0;");
        HBox nav=new HBox(8);nav.setAlignment(Pos.CENTER);nav.setPadding(new Insets(5,16,5,16));
        nav.setStyle("-fx-background-color:#16162e;-fx-border-color:#2a2a4a;-fx-border-width:0 0 1 0;");
        Button prev=nb("\u25C0");prev.setOnAction(e->goTo(pg-1));
        pgSpin=new Spinner<>(1,1,1);pgSpin.setPrefWidth(60);pgSpin.setEditable(true);
        pgSpin.valueProperty().addListener((o,a,b)->{if(b!=null&&b!=pg)goTo(b);});
        pgLbl=new Label("/ 0");pgLbl.setStyle("-fx-text-fill:#888aa;-fx-font-size:11px;");
        Button next=nb("\u25B6");next.setOnAction(e->goTo(pg+1));
        Region sep=new Region();sep.setPrefWidth(1);sep.setPrefHeight(20);sep.setStyle("-fx-background-color:#33335a;");
        zoomSlider=new Slider(50,300,150);zoomSlider.setPrefWidth(100);
        zoomSlider.setTooltip(new Tooltip("Zoom: 150%"));
        zoomSlider.valueProperty().addListener((o,a,b)->{if(isUpdatingZoom)return;zoom=b.floatValue()/100f;zoomSlider.getTooltip().setText("Zoom: "+Math.round(zoom*100)+"%");renderPage();});
        zoomLbl=new Label("150%");zoomLbl.setStyle("-fx-text-fill:#888aa;-fx-font-size:10px;");zoomLbl.setMinWidth(35);
        Button bkBtn=nb("\uD83D\uDD16");bkBtn.setTooltip(new Tooltip("Bookmark Page"));
        bkBtn.setOnAction(e->{if(bookmarks.contains(pg))bookmarks.remove(pg);else bookmarks.add(pg);refreshAnnList();});
        Button srchBtn=nb("\uD83D\uDD0D");srchBtn.setTooltip(new Tooltip("Search Ctrl+F"));
        srchBtn.setOnAction(e->toggleSearch());
        Button cpBtn=nb("\uD83D\uDCCB");cpBtn.setTooltip(new Tooltip("Copy Page Text"));
        cpBtn.setOnAction(e->copyPageText());
        ToggleButton annToggle = new ToggleButton("\uD83D\uDCDD");
        annToggle.setTooltip(new Tooltip("Toggle Annotations"));
        annToggle.setStyle("-fx-background-color:#2a2a4a;-fx-text-fill:#aac;-fx-padding:4 8;-fx-background-radius:4;-fx-cursor:hand;");
        annToggle.setSelected(true);
        annToggle.setOnAction(e -> {
            boolean show = annToggle.isSelected();
            rp.setVisible(show);
            rp.setManaged(show);
        });
        nav.getChildren().addAll(prev,pgSpin,pgLbl,next,sep,zoomSlider,zoomLbl,bkBtn,srchBtn,cpBtn,annToggle);
        searchBar=new HBox(6);searchBar.setAlignment(Pos.CENTER);searchBar.setPadding(new Insets(4,16,4,16));
        searchBar.setStyle("-fx-background-color:#1e1e3a;-fx-border-color:#2a2a4a;-fx-border-width:0 0 1 0;");
        searchBar.setVisible(false);searchBar.setManaged(false);
        searchField=new TextField();searchField.setPromptText("Search in PDF...");searchField.setPrefWidth(250);
        searchField.setStyle("-fx-background-color:#2a2a4a;-fx-text-fill:#fff;-fx-prompt-text-fill:#666;-fx-background-radius:4;-fx-padding:5 10;");
        searchField.setOnAction(e->doSearch());
        Button sPrev=nb("\u25C0");sPrev.setOnAction(e->searchNav(-1));
        Button sNext=nb("\u25B6");sNext.setOnAction(e->searchNav(1));
        searchInfo=new Label("");searchInfo.setStyle("-fx-text-fill:#888aa;-fx-font-size:10px;");
        Button sClose=nb("\u2715");sClose.setOnAction(e->toggleSearch());
        searchBar.getChildren().addAll(searchField,sPrev,sNext,searchInfo,sClose);
        top.getChildren().addAll(tBar,toolbar,nav,searchBar);
        setTop(top);
        pageIV=new ImageView();pageIV.setPreserveRatio(true);
        annCanvas=new Canvas();
        pageStack=new StackPane(pageIV,annCanvas);pageStack.setAlignment(Pos.TOP_LEFT);
        pageStack.setStyle("-fx-background-color:#fff;");
        DropShadow sh=new DropShadow();sh.setRadius(20);sh.setColor(Color.color(0,0,0,0.5));sh.setOffsetY(4);
        pageStack.setEffect(sh);
        StackPane pw=new StackPane(pageStack);pw.setPadding(new Insets(30,40,30,40));
        pw.setStyle("-fx-background-color:#2a2a4a;");pw.setAlignment(Pos.TOP_CENTER);
        annCanvas.setOnMousePressed(e->mDown(e.getX(),e.getY()));
        annCanvas.setOnMouseDragged(e->mDrag(e.getX(),e.getY()));
        annCanvas.setOnMouseReleased(e->mUp(e.getX(),e.getY()));
        ScrollPane cScroll=new ScrollPane(pw);cScroll.setFitToWidth(true);
        cScroll.setStyle("-fx-background:#2a2a4a;-fx-background-color:#2a2a4a;");
        setCenter(cScroll);
        thumbBox=new VBox(6);thumbBox.setPadding(new Insets(8));thumbBox.setAlignment(Pos.TOP_CENTER);
        thumbScroll=new ScrollPane(thumbBox);thumbScroll.setPrefWidth(120);thumbScroll.setMinWidth(100);
        thumbScroll.setFitToWidth(true);
        thumbScroll.setStyle("-fx-background:#12122a;-fx-background-color:#12122a;-fx-border-color:#2a2a4a;-fx-border-width:0 1 0 0;");
        setLeft(thumbScroll);
        rp.setPrefWidth(230);rp.setMinWidth(200);
        rp.setStyle("-fx-background-color:#12122a;-fx-border-color:#2a2a4a;-fx-border-width:0 0 0 1;");
        HBox aH=new HBox(6);aH.setAlignment(Pos.CENTER_LEFT);aH.setPadding(new Insets(10,12,10,12));
        aH.setStyle("-fx-background-color:#16162e;-fx-border-color:#2a2a4a;-fx-border-width:0 0 1 0;");
        Label aT=new Label("\uD83D\uDCDD Annotations");aT.setStyle("-fx-text-fill:#ccd;-fx-font-size:12px;-fx-font-weight:bold;");
        Region as=new Region();HBox.setHgrow(as,Priority.ALWAYS);
        Button hideBtn = new Button("Hide");
        hideBtn.setStyle("-fx-background-color:transparent;-fx-text-fill:#888aa;-fx-font-size:9px;-fx-cursor:hand;");
        hideBtn.setOnAction(e -> {
            annToggle.setSelected(false);
            rp.setVisible(false);
            rp.setManaged(false);
        });
        Button clr=new Button("Clear");clr.setStyle("-fx-background-color:transparent;-fx-text-fill:#ff6b6b;-fx-font-size:9px;-fx-cursor:hand;");
        clr.setOnAction(e->clearAnns());
        aH.getChildren().addAll(aT,as,hideBtn,clr);
        annListBox=new VBox(4);annListBox.setPadding(new Insets(8));
        ScrollPane aScr=new ScrollPane(annListBox);aScr.setFitToWidth(true);
        aScr.setStyle("-fx-background:#12122a;-fx-background-color:#12122a;");
        VBox.setVgrow(aScr,Priority.ALWAYS);
        rp.getChildren().addAll(aH,aScr);setRight(rp);
        HBox bot=new HBox(12);bot.setAlignment(Pos.CENTER_LEFT);bot.setPadding(new Insets(4,16,4,16));
        bot.setStyle("-fx-background-color:#0e0e22;-fx-border-color:#2a2a4a;-fx-border-width:1 0 0 0;");
        progressLbl=new Label("0% read");progressLbl.setStyle("-fx-text-fill:#6666aa;-fx-font-size:9px;");
        progressBar=new ProgressBar(0);progressBar.setPrefWidth(200);progressBar.setPrefHeight(8);
        progressBar.setStyle("-fx-accent:#4a6cf7;");
        bot.getChildren().addAll(progressLbl,progressBar);setBottom(bot);
        setOnKeyPressed(this::handleKey);setFocusTraversable(true);
    }
    private Button nb(String t){Button b=new Button(t);b.setStyle("-fx-background-color:#2a2a4a;-fx-text-fill:#ccd;-fx-cursor:hand;-fx-padding:4 10;-fx-background-radius:4;-fx-font-size:11px;");return b;}
    public void loadPdf(String p,int id,String t,Publication pu){
        path=p;pdfId=id;pub=pu;total=pdf.getPageCount(p);pg=1;visitedPages.clear();bookmarks.clear();
        pgLbl.setText("/ "+total);pgSpin.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1,Math.max(1,total),1));
        titleLbl.setText(t!=null?(t.length()>55?t.substring(0,52)+"\u2026":t):"PDF");
        loadThumbs();renderPage();requestFocus();
        timerService.reset();
        timerService.start();
    }
    public void loadPdf(String p,int id){loadPdf(p,id,null,null);}
    private void goTo(int p){if(p<1||p>total)return;pg=p;pgSpin.getValueFactory().setValue(p);renderPage();}
    private void renderPage(){
        if(path==null)return;
        try{
            javafx.scene.image.Image img=pdf.renderPage(path,pg,zoom);
            pageIV.setImage(img);annCanvas.setWidth(img.getWidth());annCanvas.setHeight(img.getHeight());
            if(darkMode){
                pageStack.setStyle("-fx-background-color:#1a1a1a;");
                javafx.scene.effect.ColorAdjust inv=new javafx.scene.effect.ColorAdjust();
                inv.setBrightness(-1);inv.setContrast(-1);
                pageIV.setEffect(inv);
            }else if(sepiaMode){
                pageStack.setStyle("-fx-background-color:#f4ecd8;");
                javafx.scene.effect.ColorAdjust sep2=new javafx.scene.effect.ColorAdjust();
                sep2.setHue(0.05);sep2.setSaturation(-0.5);sep2.setBrightness(0.1);
                pageIV.setEffect(sep2);
            }else{pageStack.setStyle("-fx-background-color:#fff;");pageIV.setEffect(null);}
            zoomLbl.setText(Math.round(zoom*100)+"%");
            visitedPages.add(pg);
            double pct=total>0?(visitedPages.size()*100.0/total):0;
            progressLbl.setText(Math.round(pct)+"% read ("+visitedPages.size()+"/"+total+")");
            progressBar.setProgress(pct/100.0);
            drawAnns();refreshAnnList();highlightThumb();
        }catch(Exception e){System.err.println("[PdfViewer] "+e.getMessage());}
    }
    private void drawAnns(){
        GraphicsContext gc=annCanvas.getGraphicsContext2D();
        gc.setGlobalBlendMode(javafx.scene.effect.BlendMode.SRC_OVER);
        gc.clearRect(0,0,annCanvas.getWidth(),annCanvas.getHeight());
        List<Annotation> anns=dao.getByPage(pdfId,pg);
        for(Annotation a:anns){
            String t=a.getTypeString();Color c=Color.web(a.getColor());
            if("HIGHLIGHT".equals(t)||"UNDERLINE".equals(t)){
                gc.setFill(Color.web(a.getColor(),0.3));
                gc.fillRect(a.getX()*zoom,a.getY()*zoom,a.getWidth()*zoom,a.getHeight()*zoom);
                if("UNDERLINE".equals(t)){gc.setStroke(Color.web(a.getColor(),0.8));gc.setLineWidth(2);
                    double uy=a.getY()*zoom+a.getHeight()*zoom;
                    gc.strokeLine(a.getX()*zoom,uy,a.getX()*zoom+a.getWidth()*zoom,uy);}
            }else if("STICKY_NOTE".equals(t)||"NOTE".equals(t)){
                double nx=a.getX()*zoom,ny=a.getY()*zoom;
                gc.setFill(Color.web(a.getColor(),0.15));gc.fillRoundRect(nx,ny,180,40,8,8);
                gc.setStroke(Color.web(a.getColor(),0.6));gc.setLineWidth(1);gc.strokeRoundRect(nx,ny,180,40,8,8);
                gc.setFill(Color.web(a.getColor()));gc.setFont(javafx.scene.text.Font.font(9));
                String txt=a.getContent()!=null?(a.getContent().length()>60?a.getContent().substring(0,57)+"\u2026":a.getContent()):"";
                gc.fillText("\uD83D\uDCCC "+txt,nx+6,ny+16,168);
            }else if("PEN".equals(t)&&a.getStrokeData()!=null){
                gc.setStroke(Color.web(a.getColor(),0.9));gc.setLineWidth(2);
                String[]pts=a.getStrokeData().split(";");
                for(int i=1;i<pts.length;i++){try{String[]p1=pts[i-1].split(",");String[]p2=pts[i].split(",");
                    gc.strokeLine(Double.parseDouble(p1[0]),Double.parseDouble(p1[1]),Double.parseDouble(p2[0]),Double.parseDouble(p2[1]));}catch(Exception ignored){}}
            }
        }
    }
    private void loadThumbs(){
        thumbBox.getChildren().clear();
        new Thread(()->{java.util.Map<Integer,javafx.scene.image.Image> thumbs=pdf.renderAllThumbnails(path,0.3f);
            Platform.runLater(()->{thumbBox.getChildren().clear();
                for(var entry:thumbs.entrySet()){final int p=entry.getKey();
                    ImageView iv=new ImageView(entry.getValue());iv.setFitWidth(90);iv.setPreserveRatio(true);
                    VBox card=new VBox(2);card.setAlignment(Pos.CENTER);card.setPadding(new Insets(4));
                    card.setStyle("-fx-background-color:#1a1a36;-fx-background-radius:4;-fx-cursor:hand;");
                    Label pl=new Label(""+p);pl.setStyle("-fx-text-fill:#888;-fx-font-size:9px;");
                    card.getChildren().addAll(iv,pl);
                    card.setOnMouseClicked(e->goTo(p));
                    thumbBox.getChildren().add(card);
                }highlightThumb();});
        }).start();
    }
    private void highlightThumb(){
        for(int i=0;i<thumbBox.getChildren().size();i++){
            javafx.scene.Node n=thumbBox.getChildren().get(i);
            boolean cur=(i==pg-1);
            if(n instanceof VBox v)v.setStyle(cur?"-fx-background-color:#4a6cf7;-fx-background-radius:4;-fx-cursor:hand;"
                :"-fx-background-color:#1a1a36;-fx-background-radius:4;-fx-cursor:hand;");
        }
    }
    private void refreshAnnList(){
        annListBox.getChildren().clear();
        if(!bookmarks.isEmpty()){
            Label bh=new Label("\uD83D\uDD16 Bookmarks");bh.setStyle("-fx-text-fill:#f5a623;-fx-font-size:10px;-fx-font-weight:bold;");
            annListBox.getChildren().add(bh);
            for(int bp:bookmarks){
                Label bl=new Label("  Page "+bp);bl.setStyle("-fx-text-fill:#aac;-fx-font-size:10px;-fx-cursor:hand;");
                bl.setOnMouseClicked(e->goTo(bp));annListBox.getChildren().add(bl);
            }
            annListBox.getChildren().add(new Separator());
        }
        List<Annotation> all=dao.getByPdfId(pdfId);
        if(all.isEmpty()&&bookmarks.isEmpty()){
            Label em=new Label("No annotations.\nHighlight, draw, or\nadd notes to get started.");
            em.setStyle("-fx-text-fill:#5a5a7a;-fx-font-size:10px;-fx-padding:20 10;");em.setWrapText(true);
            annListBox.getChildren().add(em);return;
        }
        for(Annotation a:all){
            VBox card=new VBox(3);card.setPadding(new Insets(6,8,6,8));
            card.setStyle("-fx-background-color:#1a1a36;-fx-background-radius:6;-fx-cursor:hand;");
            card.setOnMouseEntered(e->card.setStyle("-fx-background-color:#222244;-fx-background-radius:6;-fx-cursor:hand;"));
            card.setOnMouseExited(e->card.setStyle("-fx-background-color:#1a1a36;-fx-background-radius:6;-fx-cursor:hand;"));
            HBox tp=new HBox(6);tp.setAlignment(Pos.CENTER_LEFT);
            Label dot=new Label("\u25CF");dot.setStyle("-fx-text-fill:"+a.getColor()+";-fx-font-size:10px;");
            String ic=switch(a.getTypeString()){case"HIGHLIGHT"->"Highlight";case"PEN"->"Drawing";case"UNDERLINE"->"Underline";default->"Note";};
            Label ty=new Label(ic);ty.setStyle("-fx-text-fill:#aac;-fx-font-size:10px;-fx-font-weight:bold;");
            Region sp=new Region();HBox.setHgrow(sp,Priority.ALWAYS);
            Label pg2=new Label("p."+a.getPageNumber());pg2.setStyle("-fx-text-fill:#66a;-fx-font-size:9px;");
            Button del=new Button("\u2715");del.setStyle("-fx-background-color:transparent;-fx-text-fill:#ff6b6b;-fx-font-size:9px;-fx-cursor:hand;-fx-padding:0 3;");
            del.setOnAction(e->{dao.delete(a.getId());renderPage();});
            tp.getChildren().addAll(dot,ty,sp,pg2,del);card.getChildren().add(tp);
            if(a.getContent()!=null&&!a.getContent().isEmpty()){
                String pr=a.getContent().length()>50?a.getContent().substring(0,47)+"\u2026":a.getContent();
                Label ct=new Label(pr);ct.setStyle("-fx-text-fill:#888aa;-fx-font-size:9px;");ct.setWrapText(true);card.getChildren().add(ct);
            }
            card.setOnMouseClicked(e->{if(!(e.getTarget()instanceof Button))goTo(a.getPageNumber());});
            annListBox.getChildren().add(card);
        }
    }
    private void mDown(double x,double y){
        penSX=x;penSY=y;
        AnnotationToolbar.Tool tool=toolbar.getActiveTool();
        if(tool==AnnotationToolbar.Tool.PEN){curStroke=new StringBuilder();curStroke.append(String.format("%.0f,%.0f",x,y));}
        else if(tool==AnnotationToolbar.Tool.ERASER){delAnnAt(x,y);}
    }
    private void mDrag(double x,double y){
        AnnotationToolbar.Tool tool=toolbar.getActiveTool();
        if(tool==AnnotationToolbar.Tool.PEN&&curStroke!=null){
            GraphicsContext gc=annCanvas.getGraphicsContext2D();gc.setStroke(toolbar.getActiveColor());gc.setLineWidth(2);
            String[]l=curStroke.toString().split(";");String[]lp=l[l.length-1].split(",");
            gc.strokeLine(Double.parseDouble(lp[0]),Double.parseDouble(lp[1]),x,y);
            curStroke.append(String.format(";%.0f,%.0f",x,y));
        }else if(tool==AnnotationToolbar.Tool.HIGHLIGHT||tool==AnnotationToolbar.Tool.UNDERLINE){
            drawAnns();GraphicsContext gc=annCanvas.getGraphicsContext2D();
            gc.setFill(Color.color(toolbar.getActiveColor().getRed(),toolbar.getActiveColor().getGreen(),toolbar.getActiveColor().getBlue(),0.3));
            gc.fillRect(Math.min(penSX,x),Math.min(penSY,y),Math.abs(x-penSX),Math.abs(y-penSY));
        }else if(tool==AnnotationToolbar.Tool.CURSOR){
            drawAnns();GraphicsContext gc=annCanvas.getGraphicsContext2D();
            gc.setStroke(Color.web("#4a6cf7",0.6));gc.setLineWidth(1);gc.setLineDashes(4);
            gc.strokeRect(Math.min(penSX,x),Math.min(penSY,y),Math.abs(x-penSX),Math.abs(y-penSY));
            gc.setLineDashes(null);
        }
    }
    private void mUp(double x,double y){
        AnnotationToolbar.Tool tool=toolbar.getActiveTool();
        if(tool==AnnotationToolbar.Tool.CURSOR){
            double rx=Math.min(penSX,x)/zoom,ry=Math.min(penSY,y)/zoom;
            double rw=Math.abs(x-penSX)/zoom,rh=Math.abs(y-penSY)/zoom;
            if(rw>5&&rh>3){String txt=pdf.extractTextFromRegion(path,pg,rx,ry,rw,rh);
                if(txt!=null&&!txt.isBlank())showTextPopup(txt.trim(),x,y);}
            drawAnns();return;
        }
        if(tool==AnnotationToolbar.Tool.ERASER)return;
        Annotation ann=new Annotation();ann.setPdfId(pdfId);ann.setPageNumber(pg);ann.setColor(hex(toolbar.getActiveColor()));
        if(tool==AnnotationToolbar.Tool.HIGHLIGHT||tool==AnnotationToolbar.Tool.UNDERLINE){
            ann.setType(tool==AnnotationToolbar.Tool.UNDERLINE?"UNDERLINE":"HIGHLIGHT");
            ann.setX(Math.min(penSX,x)/zoom);ann.setY(Math.min(penSY,y)/zoom);
            ann.setWidth(Math.abs(x-penSX)/zoom);ann.setHeight(Math.abs(y-penSY)/zoom);
            if(ann.getWidth()>5&&ann.getHeight()>3)dao.save(ann);
        }else if(tool==AnnotationToolbar.Tool.PEN&&curStroke!=null){
            ann.setType("PEN");ann.setStrokeData(curStroke.toString());
            ann.setX(penSX/zoom);ann.setY(penSY/zoom);dao.save(ann);curStroke=null;
        }else if(tool==AnnotationToolbar.Tool.STICKY_NOTE){
            TextInputDialog d=new TextInputDialog();d.setTitle("Add Note");d.setHeaderText("Enter your note:");
            d.showAndWait().ifPresent(t->{ann.setType("STICKY_NOTE");ann.setContent(t);
                ann.setX(x/zoom);ann.setY(y/zoom);dao.save(ann);drawAnns();refreshAnnList();});return;
        }
        drawAnns();refreshAnnList();
    }
    private void showTextPopup(String txt,double mx,double my){
        javafx.stage.Popup popup=new javafx.stage.Popup();popup.setAutoHide(true);
        VBox box=new VBox(6);box.setPadding(new Insets(12));box.setMaxWidth(400);
        box.setStyle("-fx-background-color:#1a1a36;-fx-background-radius:8;-fx-border-color:#4a6cf7;-fx-border-radius:8;-fx-border-width:1;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.5),15,0,0,4);");
        Label hd=new Label("\uD83D\uDCCB Selected Text");hd.setStyle("-fx-text-fill:#4a6cf7;-fx-font-size:10px;-fx-font-weight:bold;");
        TextArea ta=new TextArea(txt);ta.setEditable(false);ta.setWrapText(true);ta.setPrefRowCount(Math.min(6,txt.split("\n").length+1));
        ta.setStyle("-fx-control-inner-background:#222244;-fx-text-fill:#ddd;-fx-font-size:11px;-fx-border-color:#333;-fx-border-radius:4;-fx-background-radius:4;");
        HBox btns=new HBox(6);btns.setAlignment(Pos.CENTER_LEFT);
        Button cp=new Button("\uD83D\uDCCB Copy");cp.setStyle("-fx-background-color:#4a6cf7;-fx-text-fill:#fff;-fx-font-size:10px;-fx-padding:5 14;-fx-background-radius:5;-fx-cursor:hand;-fx-font-weight:bold;");
        cp.setOnAction(e->{ClipboardContent cc=new ClipboardContent();cc.putString(txt);Clipboard.getSystemClipboard().setContent(cc);cp.setText("\u2713 Copied!");});
        Button cl=new Button("Close");cl.setStyle("-fx-background-color:#2a2a4a;-fx-text-fill:#aac;-fx-font-size:10px;-fx-padding:5 10;-fx-background-radius:5;-fx-cursor:hand;");
        cl.setOnAction(e->popup.hide());
        btns.getChildren().addAll(cp,cl);box.getChildren().addAll(hd,ta,btns);
        popup.getContent().add(box);
        javafx.geometry.Point2D pt=annCanvas.localToScreen(mx,my);
        if(pt!=null)popup.show(getScene().getWindow(),pt.getX()+10,pt.getY()+10);
    }
    private void toggleSearch(){boolean v=!searchBar.isVisible();searchBar.setVisible(v);searchBar.setManaged(v);if(v)searchField.requestFocus();}
    private void doSearch(){
        searchQuery=searchField.getText().trim();
        searchResults.clear();searchIdx=-1;
        if(searchQuery.isEmpty()){searchInfo.setText("");return;}
        searchInfo.setText("Searching...");
        final String q=searchQuery;
        new Thread(()->{
            List<Integer> found=pdf.searchAllPages(path,q);
            Platform.runLater(()->{
                searchResults.clear();searchResults.addAll(found);
                if(searchResults.isEmpty())searchInfo.setText("No results for '"+q+"'");
                else{searchIdx=0;goTo(searchResults.get(0));searchInfo.setText("1/"+searchResults.size()+" pages");}
            });
        }).start();
    }
    private void searchNav(int dir){
        if(searchResults.isEmpty())return;searchIdx=Math.floorMod(searchIdx+dir,searchResults.size());
        goTo(searchResults.get(searchIdx));searchInfo.setText((searchIdx+1)+"/"+searchResults.size());
    }
    private void copyPageText(){
        String t=pdf.extractPageText(path,pg);if(t!=null&&!t.isBlank()){
            ClipboardContent cc=new ClipboardContent();cc.putString(t.trim());Clipboard.getSystemClipboard().setContent(cc);}
    }
    private void copyCitation(){
        if(pub==null){new Alert(Alert.AlertType.WARNING,"No publication linked to this PDF.").showAndWait();return;}
        try{
            CitationFormatter f=new APAFormatter();String c=f.format(pub);
            ClipboardContent cc=new ClipboardContent();cc.putString(c);Clipboard.getSystemClipboard().setContent(cc);
            new Alert(Alert.AlertType.INFORMATION,"Citation copied to clipboard!\n\n"+c).showAndWait();
        }catch(Exception ex){new Alert(Alert.AlertType.ERROR,"Citation error: "+ex.getMessage()).showAndWait();}
    }
    private void handleKey(KeyEvent e){
        if(e.getCode()==KeyCode.RIGHT||e.getCode()==KeyCode.PAGE_DOWN)goTo(pg+1);
        else if(e.getCode()==KeyCode.LEFT||e.getCode()==KeyCode.PAGE_UP)goTo(pg-1);
        else if(e.getCode()==KeyCode.HOME)goTo(1);
        else if(e.getCode()==KeyCode.END)goTo(total);
        else if(e.isControlDown()&&e.getCode()==KeyCode.F)toggleSearch();
        else if(e.isControlDown()&&e.getCode()==KeyCode.C)copyPageText();
        else if(e.getCode()==KeyCode.EQUALS||e.getCode()==KeyCode.PLUS){zoom=Math.min(3,zoom+0.25f);isUpdatingZoom=true;zoomSlider.setValue(zoom*100);isUpdatingZoom=false;renderPage();}
        else if(e.getCode()==KeyCode.MINUS){zoom=Math.max(0.5f,zoom-0.25f);isUpdatingZoom=true;zoomSlider.setValue(zoom*100);isUpdatingZoom=false;renderPage();}
        else if(e.getCode()==KeyCode.B){if(bookmarks.contains(pg))bookmarks.remove(pg);else bookmarks.add(pg);refreshAnnList();}
    }
    private void delAnnAt(double mx,double my){
        for(Annotation a:dao.getByPage(pdfId,pg)){double ax=a.getX()*zoom,ay=a.getY()*zoom;
            double aw=Math.max(a.getWidth()*zoom,26),ah=Math.max(a.getHeight()*zoom,26);
            if(mx>=ax&&mx<=ax+aw&&my>=ay&&my<=ay+ah){dao.delete(a.getId());drawAnns();refreshAnnList();return;}}
    }
    private void clearAnns(){
        Alert c=new Alert(Alert.AlertType.CONFIRMATION,"Delete all annotations?",ButtonType.YES,ButtonType.NO);
        c.setHeaderText(null);c.showAndWait().ifPresent(b->{if(b==ButtonType.YES){dao.getByPdfId(pdfId).forEach(a->dao.delete(a.getId()));renderPage();}});
    }
    private void exportPdf(){
        if(path==null)return;javafx.stage.FileChooser ch=new javafx.stage.FileChooser();ch.setTitle("Export");ch.setInitialFileName("annotated.pdf");
        java.io.File dl=new java.io.File(System.getProperty("user.home"),"Downloads");if(dl.exists())ch.setInitialDirectory(dl);
        ch.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF","*.pdf"));
        java.io.File f=ch.showSaveDialog(getScene().getWindow());
        if(f!=null){try{pdf.exportAnnotatedPdf(path,dao.getByPdfId(pdfId),f.getAbsolutePath());
            new Alert(Alert.AlertType.INFORMATION,"Saved: "+f.getAbsolutePath()).showAndWait();}catch(Exception ex){new Alert(Alert.AlertType.ERROR,"Export failed: "+ex.getMessage()).showAndWait();}}
    }
    private String hex(Color c){return String.format("#%02X%02X%02X",(int)(c.getRed()*255),(int)(c.getGreen()*255),(int)(c.getBlue()*255));}
}
