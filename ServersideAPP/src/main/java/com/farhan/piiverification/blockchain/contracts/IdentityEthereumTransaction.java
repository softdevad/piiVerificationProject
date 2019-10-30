package com.farhan.piiverification.blockchain.contracts;

//import com.identity.publicblockchain.Kyc;


import com.farhan.piiverification.blockchain.Utilities.CryptoUtilities;
import com.farhan.piiverification.blockchain.Utilities.PropertiesIO;
import com.farhan.piiverification.blockchain.contracts.AuthorizedPII_v1.AuthorizedPII;
//import com.farhan.piiverification.blockchain.contracts.identity_v2.Identity;
import com.farhan.piiverification.blockchain.contracts.AuthorizedRegistry.AuthorizedRegistry;
import com.farhan.piiverification.blockchain.core.ContractTransaction;
import com.farhan.piiverification.blockchain.core.EthereumAccount;
import com.farhan.piiverification.blockchain.core.EthereumEngine;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.ens.contracts.generated.ENS;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.core.methods.response.Web3Sha3;
import org.web3j.tuples.generated.Tuple2;
import org.web3j.tx.Contract;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

//import com.ateon.KycBlockchainDemo.Utilities.PropertiesIO;
//import com.ateon.KycBlockchainDemo.domains.Person;

public class IdentityEthereumTransaction extends EthereumEngine implements ContractTransaction {

    private EthereumAccount account=null;
    private String smartContractAddress, registryContractAddress;
    private List<String> authorityAddresses;

    // Variables to be stored
    private String age_hash ;
    private String name_hash;
    private String address_hash;
    private String IPFS_ImageCid;
    private String identityWallet;


    static public int ETHEREUM_GANACHE = 1;
    static public int ETHEREUM_TRUFFLE = 2;
    static public int ETHEREUM_ROSPOTEN=3;

    private int ETHEREUM_TYP_NETWORK =-1;

    final String resourceFullPath = "ServerSideAPP/src/main/resources/"; //"ServersideAPP/src/main/resources/"

    private boolean useHashData = false;

    //only authority addresses which will be provided can only make transactions
    public IdentityEthereumTransaction(int ethereumType) throws Exception {

        this.ETHEREUM_TYP_NETWORK = ethereumType;

        if(loadEthereumProperties())
        {
            //deploy contract if it does not exist and if the network is from ganache or truffle
            if(!this.isContractExist())
            {
                if(this.ETHEREUM_TYP_NETWORK==ETHEREUM_GANACHE || this.ETHEREUM_TYP_NETWORK==ETHEREUM_TRUFFLE ) {
                    boolean val = this.deployContract();
                    String num = this.getLatestBlockNumber();
                    this.deployContractVersionRegistry("ChocolateCake",smartContractAddress);
                }
            }

            if(this.ETHEREUM_TYP_NETWORK==ETHEREUM_ROSPOTEN)
            {
               this.smartContractAddress = this.getAddressfromEth();
            }
        }
    }

    public String getIPFS_ImageCid() {
        return IPFS_ImageCid;
    }

    public void setIPFS_ImageCid(String IPFS_ImageCid) {
        this.IPFS_ImageCid = IPFS_ImageCid;
    }

    public boolean isUseHashData() {
        return useHashData;
    }

    public void setUseHashData(boolean useHashData) {
        this.useHashData = useHashData;
    }

    public void setCallingAccount(String strAccountKey)
    {
        this.account = new EthereumAccount(strAccountKey);
    }

    public void setAge_hash(String age_hash) {
        this.age_hash = age_hash;
    }

    public void setName_hash(String name_hash) {
        this.name_hash = name_hash;
    }

    public void setAddress_hash(String address_hash) {
        this.address_hash = address_hash;
    }

    public String getIdentityWallet() {
        return identityWallet;
    }

    public void setIdentityWallet(String identityWallet) {
        this.identityWallet = identityWallet;
    }



    @Override
    public boolean deployContract() {

        try
        {
            if(account==null) throw new Exception("No caller account initialized");

            //check if same contract is already deployed

            if(!isContractExist())
            {
                //Identity contract = null;
                ContractGasProvider contractGasProvider = new DeployGasProvider();

                // The admin ownership will adjusted internally by the Ownable interface of the AuthorizedPII contract so no need to give explicit any admin address like before
                AuthorizedPII contract = AuthorizedPII.deploy(web3a,account.getCredentials(),contractGasProvider).send();  ;//Identity.deploy(web3a,account.getCredentials(),contractGasProvider,authorityAddresses).send();

                smartContractAddress = contract.getContractAddress();

                //save new contract in properties file
                saveContractDetailInProperty(account.getAccountAddress(), smartContractAddress,Integer.toString(this.ETHEREUM_TYP_NETWORK));

                log.info("Smart contract deployed to address " + smartContractAddress);

                return true;
            }


        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public String insertInContract() {

        getAddressfromEth();

        hashAllData();

        String strTransactionInfo = "";
        Date date= new Date();
        long timestamp = CryptoUtilities.getUnixTimeStamp();

        try
        {

            if(this.useHashData)
            {
                //since all these were stright text therefore we will save hashes of the text but for photos we will just save cid without sha3
                this.name_hash = web3a.web3Sha3(this.name_hash).send().getResult();
                this.address_hash = web3a.web3Sha3(this.address_hash).send().getResult();
                this.age_hash = web3a.web3Sha3(this.age_hash).send().getResult();
            }

            ContractGasProvider contractGasProvider = new DeployGasProvider();
            Contract contract = AuthorizedPII.load(getSmartContractAddress(),web3a,account.getCredentials(),contractGasProvider);

            TransactionReceipt tr = ((AuthorizedPII) contract).
                    addTransaction(this.name_hash,this.address_hash,this.age_hash,this.IPFS_ImageCid, this.identityWallet).send();



            List<AuthorizedPII.SubmissionEventEventResponse> eventss =  ((AuthorizedPII) contract).getSubmissionEventEvents(tr);

            log.info("Transaction Receipt: " + tr.toString());

            //get the event's parameter which i named it as transactionid in the solidity contract
            log.info(" SubmissionEvent Value: "+ eventss.get(0).transactionid);

            strTransactionInfo = tr.getTransactionHash();

        } catch (Exception e) {
            e.printStackTrace();
        }



        return strTransactionInfo;

    }

    @Override
    public String retrieveFromContract() {

        String strTransactionInfo = "";
        //  String searchIndexHash = CryptoUtilities.convertToHash(searchIndex,"SHA-256");
        try
        {

            ContractGasProvider contractGasProvider = new DeployGasProvider();
            Contract contract = AuthorizedPII.load(getSmartContractAddress(),web3a,account.getCredentials(),contractGasProvider);

            BigInteger fees = new BigInteger("1");

            //this.address_hash =
            TransactionReceipt recAddress = ((AuthorizedPII) contract).getAddress(this.identityWallet,fees).send();
            // this.age_hash =
            TransactionReceipt recAge = ((AuthorizedPII) contract).getAge(this.identityWallet,fees).send();
            // this.name_hash =
            TransactionReceipt recName = ((AuthorizedPII) contract).getName(this.identityWallet,fees).send();

            // get ipfs cid
            String ipfs_cid = ((AuthorizedPII) contract).getPhoto(this.identityWallet).send();


            // get all three transaction's events (Since we cannot get return data in transaction type function where we have to pay money)

            List<AuthorizedPII.NameEventEventResponse> nameEvent = ((AuthorizedPII) contract).getNameEventEvents(recName);
            List<AuthorizedPII.AgeEventEventResponse> ageEvent = ((AuthorizedPII) contract).getAgeEventEvents(recAge);
            List<AuthorizedPII.AddressEventEventResponse> addressEvent = ((AuthorizedPII) contract).getAddressEventEvents(recAddress);

            this.name_hash = nameEvent.get(0).name;
            this.address_hash = addressEvent.get(0).homeaddress;
            this.age_hash = ageEvent.get(0).age;
            this.IPFS_ImageCid = ipfs_cid;

            log.info("Name :" +this.name_hash+ " ---- " +
                    "age : "+this.age_hash+" address: "+ this.address_hash+" ipfs_cid: "+this.IPFS_ImageCid);

            // TransactionReceipt transactionReceipt =   ((Identity) contract).getTransactionReceipt().get();

            strTransactionInfo = "Successful";// transactionReceipt.getTransactionHash();

        } catch (Exception e) {
            e.printStackTrace();
        }


        return strTransactionInfo;
    }

    private void hashAllData()
    {
        //      iqama_hash = CryptoUtilities.convertToHash(person.getIqama(),"SHA-256");
        //      age_hash = CryptoUtilities.convertToHash(person.getAge()+"","SHA-256");
        //      name_hash = CryptoUtilities.convertToHash(person.getFullName(),"SHA-256");
        //     address_hash = CryptoUtilities.convertToHash(person.getAddress(),"SHA-256");
        //    email_hash = CryptoUtilities.convertToHash(person.getEmail(),"SHA-256");
    }



    @Override
    public boolean isContractExist() {

        String num = this.getLatestBlockNumber();

        if(Integer.parseInt(num)==0)
        {
            log.info("Ethereum network of "+this.ETHEREUM_TYP_NETWORK+" is running first time");
            return false;
        }
        else if(getSmartContractAddress()==null)
            return false;
        else  if(getSmartContractAddress().length()>0)
            return true;
        else
            return false;
    }

    private void saveContractDetailInProperty(String owner, String contractaddress, String blockchainNetworkType)
    {
        PropertiesIO p= new PropertiesIO("smartcontract.properties",resourceFullPath);

        p.saveProperties(new HashMap<String, String>() {{
            put("blockchainNetwork",blockchainNetworkType);
            put("smartContractAddress", contractaddress);
            put("contractOwner", owner);

        }});
    }

    @Override
    public String getSmartContractAddress() {

        //check if same contract is already deployed
        PropertiesIO p= new PropertiesIO("smartcontract.properties",resourceFullPath);
        String contractaddress = p.loadPropertyByKey("smartContractAddress");
        return contractaddress;
    }

    public int getLastBlockchainNetworkType() {

        //check if same contract is already deployed
        PropertiesIO p= new PropertiesIO("smartcontract.properties",resourceFullPath);
        String bctype = p.loadPropertyByKey("blockchainNetwork");


        int ret = ((bctype==null)||(bctype.length()==0))?-1:Integer.parseInt(bctype);

        return ret;
    }

    private void resetSmartContractProperties()
    {
        log.info("Reset all the data in smartcontract.properties since network was changed");
        saveContractDetailInProperty("","","");
    }





    public String getAge_hash() {
        return age_hash;
    }

    public String getName_hash() {
        return name_hash;
    }

    public String getAddress_hash() {
        return address_hash;
    }

    public void clearAllData()
    {
        age_hash = "";
        name_hash = "";
        address_hash = "";
    }


    private boolean loadEthereumProperties() throws Exception {

        String ownerAccount;
        String ownerPvtKey;
        String link_address;
        PropertiesIO p= null;

        if(getLastBlockchainNetworkType()!= this.ETHEREUM_TYP_NETWORK)
        {
            log.info("Seems like network is changed in the application , it is now must to reset previous smartcontract.properties file");
            resetSmartContractProperties();
        }

        String bcnetwork_config_file ="";
        if(this.ETHEREUM_TYP_NETWORK==ETHEREUM_GANACHE) {
            bcnetwork_config_file = "blockchain.ganache.properties";
            p = new PropertiesIO(bcnetwork_config_file,resourceFullPath);
        }
        else if(this.ETHEREUM_TYP_NETWORK==ETHEREUM_TRUFFLE) {
            bcnetwork_config_file = "blockchain.truffle.properties";
            p = new PropertiesIO(bcnetwork_config_file,resourceFullPath);
        }
        else if(this.ETHEREUM_TYP_NETWORK==ETHEREUM_ROSPOTEN) {
            bcnetwork_config_file = "blockchain.ropsten.properties";
            p = new PropertiesIO(bcnetwork_config_file,resourceFullPath);
        }

        log.info("Get network details from "+bcnetwork_config_file);

        ownerAccount = p.loadPropertyByKey("contract_owner_address");
        ownerPvtKey = p.loadPropertyByKey("contract_owner_privatekey");
        link_address = p.loadPropertyByKey("link_address");



        if(this.ETHEREUM_TYP_NETWORK==ETHEREUM_ROSPOTEN) {

            log.info("Save Ropsten network information from blockchain.ropsten.properties to smartcontract.properties");
            this.smartContractAddress = p.loadPropertyByKey("contract_address");
            this.registryContractAddress = p.loadPropertyByKey("registryContractAddress");

            //if its new then overwrite it again
            if((getSmartContractAddress() != this.smartContractAddress) || (this.smartContractAddress.isEmpty())) {
                this.saveContractDetailInProperty(ownerAccount, this.smartContractAddress, Integer.toString(this.ETHEREUM_TYP_NETWORK));
                this.saveRegistryContractDetailInProperty(ownerAccount, this.registryContractAddress,Integer.toString(this.ETHEREUM_TYP_NETWORK));
            }
        }


        this.account = new EthereumAccount(ownerPvtKey);
        this.authorityAddresses = Arrays.asList(ownerAccount);

        if(this.connectBlockchainServer(link_address)==0)
            throw new Exception("Unable to connect with the blockchain RPC on "+link_address);

        if(this.account == null) throw new Exception("Contract Owner Wallet Account and Privatekey is incorrect or missing ");

        return true;
    }



    @Override
    public boolean deployContractVersionRegistry(String version_name,String contract_address)
    {
        try
        {
            if(account==null) throw new Exception("No caller account initialized");

            //check if same contract is already deployed
            if(isContractExist())
            {
                //Identity contract = null;
                ContractGasProvider contractGasProvider = new DeployGasProvider();

                // The admin ownership will adjusted internally by the Ownable interface of the AuthorizedPII contract so no need to give explicit any admin address like before
                AuthorizedRegistry contract = AuthorizedRegistry.deploy(web3a,account.getCredentials(),contractGasProvider,version_name,contract_address).send();  ;//Identity.deploy(web3a,account.getCredentials(),contractGasProvider,authorityAddresses).send();

                String registryContractAddress = registryContractAddress = contract.getContractAddress();

                //save new contract in properties file
                saveRegistryContractDetailInProperty(account.getAccountAddress(), registryContractAddress,Integer.toString(this.ETHEREUM_TYP_NETWORK));

                log.info("Registry Smart contract deployed to address " + registryContractAddress);

                log.info("Lets check the value which were added in registry smart contract" + registryContractAddress);



                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    String getAddressfromEth()
    {
        String mainContractAddress="";
        String registryEthAddress = getRegistryContractAddress();
        ContractGasProvider contractGasProvider = new DeployGasProvider();
        Contract contract = AuthorizedRegistry.load(registryEthAddress,web3a,account.getCredentials(),contractGasProvider);

        try {
            Tuple2<String, BigInteger> tr = ((AuthorizedRegistry) contract).getLatestVersion().send();
            mainContractAddress = tr.component1();
            String version = tr.component2().toString();
            log.info("ROPSTEN network domain "+ registryEthAddress + " loaded successfully");
            log.info("RegistryAuthority Contract Called getLatestVersion of AuthorityPII Contract");
            log.info("Retrieved Version "+ version);
            log.info("Retrieved AuthorityPII Address"+ mainContractAddress);

            //if retrieved address is not stored in properties files then save it again
            if(mainContractAddress!=this.smartContractAddress)
            {
                this.saveContractDetailInProperty(this.account.getAccountAddress(),mainContractAddress,Integer.toString(this.ETHEREUM_TYP_NETWORK));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return mainContractAddress;
    }


    private void saveRegistryContractDetailInProperty(String owner, String contractaddress, String blockchainNetworkType)
    {
        PropertiesIO p= new PropertiesIO("smartcontract_registry.properties",resourceFullPath);

        p.saveProperties(new HashMap<String, String>() {{
            put("blockchainNetwork",blockchainNetworkType);
            put("smartContractAddress", contractaddress);
            put("contractOwner", owner);
        }});
    }

    public String getRegistryContractAddress()
    {
        PropertiesIO p= new PropertiesIO("smartcontract_registry.properties",resourceFullPath);
        String registryContract = p.loadPropertyByKey("smartContractAddress");
        return registryContract;
    }

}

