package org.alfresco.po.share.wqs;

import org.alfresco.po.share.SharePage;
import org.alfresco.webdrone.RenderTime;
import org.alfresco.webdrone.WebDrone;
import org.alfresco.webdrone.exception.PageOperationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.alfresco.webdrone.RenderElement.getVisibleRenderElement;

public abstract class WcmqsAbstractPage extends SharePage
{
    public final static By PAGE_MENU = By.cssSelector("div[id='myslidemenu']");
    public final static By CONTACT_MENU = By.cssSelector("div.link-menu");
    public final static By CONTACT_MENU_URL = By.cssSelector("div[class='link-menu']>ul>li>a");
    public final static By ALFRESCO_LOGO = By.cssSelector("div[id='logo']");
    public final static By ALFRESCO_LOGO_URL = By.cssSelector("div[id='logo']>a");
    public final static By SEARCH_FIELD = By.cssSelector("input[id='search-phrase']");
    public final static By SEARCH_BUTTON = By.cssSelector("input.input-arrow");
    public final static By ALFRSCO_BOTTOM_URL = By.cssSelector("div[id='footer']>div[class='copyright']>a");
    public final static By NEWS_MENU = By.cssSelector("a[href$='news/']");
    public final static By HOME_MENU = By.xpath("//div[@id='myslidemenu']//a[text()='Home']");
    public final static By PUBLICATIONS_MENU = By.cssSelector("a[href$='publications/']");
    public final static By BLOG_MENU = By.cssSelector("a[href$='blog/']");
    public final static String NEWS_MENU_STR = "news";
    public final static String BLOG_MENU_STR = "blog";
    public static final String PUBLICATIONS_MENU_STR = "publications";
    public static final String HOME_MENU_STR = "home";
    private static Log logger = LogFactory.getLog(WcmqsAbstractPage.class);

    public WcmqsAbstractPage(WebDrone drone)
    {
        super(drone);
    }

    @SuppressWarnings("unchecked")
    @Override
    public WcmqsAbstractPage render(RenderTime renderTime)
    {
        elementRender(renderTime, getVisibleRenderElement(PAGE_MENU), getVisibleRenderElement(CONTACT_MENU));
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public WcmqsAbstractPage render()
    {
        return render(new RenderTime(maxPageLoadingTime));
    }

    @SuppressWarnings("unchecked")
    @Override
    public WcmqsAbstractPage render(final long time)
    {
        return render(new RenderTime(time));
    }

    /**
     * Method to verify the contact link exists
     * 
     * @return
     */
    public boolean isContactLinkDisplay()
    {
        try
        {
            return drone.findAndWait(CONTACT_MENU).isDisplayed();
        }
        catch (TimeoutException e)
        {
            return false;
        }
    }

    /**
     * Method to verify the page menu exists
     * 
     * @return
     */
    public boolean isPageMenuDisplay()
    {
        try
        {
            return drone.findAndWait(PAGE_MENU).isDisplayed();
        }
        catch (TimeoutException e)
        {
            return false;
        }
    }

    /**
     * Method to verify the Alfresco logo exists
     * 
     * @return
     */
    public boolean isAlfrescoLogoDisplay()
    {
        try
        {
            return drone.findAndWait(ALFRESCO_LOGO).isDisplayed();
        }
        catch (TimeoutException e)
        {
            return false;
        }
    }

    /**
     * Method to verify the search field with search button exists
     * 
     * @return
     */
    public boolean isSearchFieldWithButtonDisplay()
    {
        try
        {
            return drone.findAndWait(SEARCH_FIELD).isDisplayed() && drone.findAndWait(SEARCH_BUTTON).isDisplayed();
        }
        catch (TimeoutException e)
        {
            return false;
        }
    }

    public boolean isBottomUrlDisplayed()
    {
        try
        {
            drone.findAndWait(ALFRSCO_BOTTOM_URL);
            return true;
        }
        catch (TimeoutException e)
        {
            throw new PageOperationException("Exceeded time to find bottom URL. " + e.toString());
        }
    }

    public void selectMenu(String menuOption)
    {
        WebElement webElement = null;
        switch (menuOption.toLowerCase())
        {
            case "home":
            {
                webElement = drone.findAndWait(HOME_MENU);
                break;
            }
            case "news":
            {
                webElement = drone.findAndWait(NEWS_MENU);
                break;
            }
            case "publications":
            {
                webElement = drone.findAndWait(PUBLICATIONS_MENU);
                break;
            }
            case "blog":
            {
                webElement = drone.findAndWait(BLOG_MENU);
                break;
            }
            default:
            {
                webElement = drone.findAndWait(By.cssSelector(String.format("a[href$='%s/']", menuOption)));
                break;
            }

        }
        try
        {
            webElement.click();
        }
        catch (TimeoutException e)
        {
            throw new PageOperationException("Exceeded time to find and click " + menuOption + " menu. " + e.toString());
        }
    }

    public WcmqsHomePage clickWebQuickStartLogo()
    {
        try
        {
            drone.findAndWait(ALFRESCO_LOGO_URL).click();
            return new WcmqsHomePage(drone);
        }
        catch (TimeoutException e)
        {
            throw new PageOperationException("Exceeded time to find and click the Alfresco Web Quick Start Logo");
        }
    }

    public void clickContactLink()
    {
        try
        {
            drone.findAndWait(CONTACT_MENU_URL).click();
        }
        catch (TimeoutException e)
        {
            throw new PageOperationException("Exceeded time to find and click the contact link.");
        }
    }

    public void clickAlfrescoLink()
    {
        try
        {
            drone.findAndWait(ALFRSCO_BOTTOM_URL).click();
        }
        catch (TimeoutException e)
        {
            throw new PageOperationException("Exceeded time to find and click the bottom Alfresco link.");
        }
    }

    /**
     * Method to input test is the search field
     * 
     * @return
     */
    public void inputTextInSearchField(String searchedText)
    {
        drone.findAndWait(SEARCH_FIELD).clear();
        drone.findAndWait(SEARCH_FIELD, SECONDS.convert(maxPageLoadingTime, MILLISECONDS)).sendKeys(searchedText);
    }

    /**
     * Method to click search button
     * 
     * @return
     */
    public void clickSearchButton()
    {
        drone.findAndWait(SEARCH_BUTTON).click();
    }

    /**
     * Method to enter searched text and click search button
     * 
     * @return
     */
    public void searchText(String searchedText)
    {
        try
        {
            logger.info("Search text " + searchedText);
            inputTextInSearchField(searchedText);
            clickSearchButton();
        }
        catch (TimeoutException te)
        {
            throw new PageOperationException("Exceeded time to find search button. " + te.toString());
        }
    }
}
